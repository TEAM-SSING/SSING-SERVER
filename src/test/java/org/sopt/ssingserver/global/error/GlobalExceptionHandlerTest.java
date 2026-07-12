package org.sopt.ssingserver.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.sopt.ssingserver.global.logging.RequestIdFilter;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTest {

    @Test
    void 비즈니스_필드_검증_실패를_VALIDATION_FAILED_응답으로_변환한다() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(new ErrorResponseFactory());
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/instructors/me/matching-exposure");
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "req-90");
        BusinessValidationException exception = BusinessValidationException.of(
                "sport",
                "보유 자격증으로 선택할 수 없는 종목입니다."
        );

        ResponseEntity<BaseResponse<Void>> response =
                handler.handleBusinessValidationException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().errors())
                .containsEntry("sport", "보유 자격증으로 선택할 수 없는 종목입니다.");
        assertThat(response.getBody().requestId()).isEqualTo("req-90");
    }

    @Test
    void 예상된_4xx_비즈니스_예외는_ERROR를_남기지_않는다() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = attachAppender(logger);

        try {
            GlobalExceptionHandler handler = new GlobalExceptionHandler(new ErrorResponseFactory());
            MockHttpServletRequest request = requestWithRequestId("req-400");

            handler.handleBusinessException(new BusinessException(CommonErrorCode.BAD_REQUEST), request);

            assertThat(appender.list).isEmpty();
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void 처리되지_않은_5xx_예외는_민감한_원인_없이_구조화_ERROR를_한_번_남긴다() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = attachAppender(logger);

        try {
            GlobalExceptionHandler handler = new GlobalExceptionHandler(new ErrorResponseFactory());
            MockHttpServletRequest request = requestWithRequestId("req-500");
            MDC.put(RequestIdFilter.REQUEST_ID_MDC_KEY, "req-500");

            handler.handleException(new IllegalStateException("secret-detail"), request);

            assertThat(appender.list).hasSize(1);
            ILoggingEvent event = appender.list.getFirst();
            assertThat(event.getLevel()).isSameAs(Level.ERROR);
            assertThat(event.getFormattedMessage()).isEqualTo("Unhandled server exception");
            assertThat(event.getThrowableProxy()).isNull();
            assertThat(event.getFormattedMessage()).doesNotContain("secret-detail", "req-500");
            assertThat(event.getMDCPropertyMap()).containsEntry("request_id", "req-500");
            assertThat(keyValueMap(event))
                    .containsEntry("event", "http.request.unhandled_exception")
                    .containsEntry("error_code", "INTERNAL_ERROR")
                    .containsEntry("status", 500)
                    .containsEntry("exception_type", IllegalStateException.class.getName());
        } finally {
            MDC.remove(RequestIdFilter.REQUEST_ID_MDC_KEY);
            logger.detachAppender(appender);
        }
    }

    @Test
    void 내부_5xx_비즈니스_예외는_구조화_ERROR를_한_번_남긴다() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = attachAppender(logger);

        try {
            GlobalExceptionHandler handler = new GlobalExceptionHandler(new ErrorResponseFactory());
            MDC.put(RequestIdFilter.REQUEST_ID_MDC_KEY, "req-business-500");

            handler.handleBusinessException(
                    new BusinessException(CommonErrorCode.INTERNAL_ERROR),
                    requestWithRequestId("req-business-500")
            );

            assertThat(appender.list).hasSize(1);
            ILoggingEvent event = appender.list.getFirst();
            assertThat(event.getThrowableProxy()).isNull();
            assertThat(event.getMDCPropertyMap()).containsEntry("request_id", "req-business-500");
            assertThat(keyValueMap(event))
                    .containsEntry("event", "http.request.unhandled_exception")
                    .containsEntry("error_code", "INTERNAL_ERROR")
                    .containsEntry("status", 500)
                    .containsEntry("exception_type", BusinessException.class.getName());
        } finally {
            MDC.remove(RequestIdFilter.REQUEST_ID_MDC_KEY);
            logger.detachAppender(appender);
        }
    }

    @Test
    void 외부서비스_503은_전역처리기에서_중복_ERROR를_남기지_않는다() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = attachAppender(logger);

        try {
            GlobalExceptionHandler handler = new GlobalExceptionHandler(new ErrorResponseFactory());

            handler.handleBusinessException(
                    new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_UNAVAILABLE),
                    requestWithRequestId("req-external-503")
            );

            assertThat(appender.list).isEmpty();
        } finally {
            logger.detachAppender(appender);
        }
    }

    private MockHttpServletRequest requestWithRequestId(String requestId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, requestId);
        return request;
    }

    private ListAppender<ILoggingEvent> attachAppender(Logger logger) {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private Map<String, Object> keyValueMap(ILoggingEvent event) {
        return event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(keyValuePair -> keyValuePair.key, keyValuePair -> keyValuePair.value));
    }
}
