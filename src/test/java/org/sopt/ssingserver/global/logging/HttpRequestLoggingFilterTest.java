package org.sopt.ssingserver.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.sopt.ssingserver.global.error.ErrorCode;
import org.sopt.ssingserver.global.monitoring.ClientErrorTrackingPolicy;
import org.sopt.ssingserver.global.monitoring.ErrorTracker;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

class HttpRequestLoggingFilterTest {

    @Test
    void logsOneStructuredCompletionEventWithRequestId() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(HttpRequestLoggingFilter.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);

        try {
            MDC.put(RequestIdFilter.REQUEST_ID_MDC_KEY, "req-123");
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/songs");
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/songs/{songId}");
            MockHttpServletResponse response = new MockHttpServletResponse();

            new HttpRequestLoggingFilter().doFilter(request, response, (servletRequest, servletResponse) ->
                    ((MockHttpServletResponse) servletResponse).setStatus(201));

            assertThat(appender.list).hasSize(1);
            ILoggingEvent event = appender.list.getFirst();
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).isEqualTo("HTTP request completed");
            assertThat(event.getMDCPropertyMap()).containsEntry("request_id", "req-123");
            assertThat(keyValueMap(event))
                    .containsEntry("event", "http.request.completed")
                    .containsEntry("method", "POST")
                    .containsEntry("path", "/api/songs/{songId}")
                    .containsEntry("status", 201);
            assertThat(keyValueMap(event).get("duration_ms")).isInstanceOf(Long.class);
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
            MDC.remove(RequestIdFilter.REQUEST_ID_MDC_KEY);
        }
    }

    @Test
    void 정상_health_폴링은_완료_로그를_남기지_않는다() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(HttpRequestLoggingFilter.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);

        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
            MockHttpServletResponse response = new MockHttpServletResponse();

            new HttpRequestLoggingFilter().doFilter(request, response, (servletRequest, servletResponse) ->
                    ((MockHttpServletResponse) servletResponse).setStatus(200));

            assertThat(appender.list).isEmpty();
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
        }
    }

    @Test
    void 비정상_health_응답은_완료_로그를_남긴다() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(HttpRequestLoggingFilter.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);

        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
            MockHttpServletResponse response = new MockHttpServletResponse();

            new HttpRequestLoggingFilter().doFilter(request, response, (servletRequest, servletResponse) ->
                    ((MockHttpServletResponse) servletResponse).setStatus(503));

            assertThat(appender.list).hasSize(1);
            assertThat(keyValueMap(appender.list.getFirst())).containsEntry("status", 503);
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
        }
    }

    @Test
    void 매핑_템플릿이_없으면_unmapped와_raw_URI를_함께_기록한다() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(HttpRequestLoggingFilter.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);

        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/unknown/private-value");
            MockHttpServletResponse response = new MockHttpServletResponse();

            new HttpRequestLoggingFilter().doFilter(request, response, (servletRequest, servletResponse) ->
                    ((MockHttpServletResponse) servletResponse).setStatus(404));

            assertThat(keyValueMap(appender.list.getFirst()))
                    .containsEntry("path", "/unmapped")
                    .containsEntry("raw_path", "/unknown/private-value");
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
        }
    }

    @Test
    void 명시되지_않은_4xx는_Sentry_추적기로_전달한다() throws Exception {
        RecordingErrorTracker errorTracker = new RecordingErrorTracker();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/unknown/path");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new HttpRequestLoggingFilter(errorTracker).doFilter(request, response, (servletRequest, servletResponse) ->
                ((MockHttpServletResponse) servletResponse).setStatus(400));

        assertThat(errorTracker.capturedClientErrorRequest).isSameAs(request);
        assertThat(errorTracker.capturedClientErrorStatus).isEqualTo(400);
    }

    @Test
    void 명시된_4xx는_Sentry_추적기로_전달하지_않는다() throws Exception {
        RecordingErrorTracker errorTracker = new RecordingErrorTracker();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
        ClientErrorTrackingPolicy.markDeclared(request);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new HttpRequestLoggingFilter(errorTracker).doFilter(request, response, (servletRequest, servletResponse) ->
                ((MockHttpServletResponse) servletResponse).setStatus(401));

        assertThat(errorTracker.capturedClientErrorRequest).isNull();
    }

    @Test
    void 처리되지_않은_filter_RuntimeException은_ERROR_한_번과_500_완료_로그를_남긴다() {
        Logger logger = (Logger) LoggerFactory.getLogger(HttpRequestLoggingFilter.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);
        RecordingErrorTracker errorTracker = new RecordingErrorTracker();

        try {
            MDC.put(RequestIdFilter.REQUEST_ID_MDC_KEY, "req-filter-500");
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test/42");
            request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "req-filter-500");
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/test/{testId}");
            MockHttpServletResponse response = new MockHttpServletResponse();

            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(() -> new HttpRequestLoggingFilter(errorTracker).doFilter(
                            request,
                            response,
                            (servletRequest, servletResponse) -> {
                                throw new IllegalStateException("secret-filter-detail");
                            }
                    ));

            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(appender.list).hasSize(2);

            ILoggingEvent errorEvent = appender.list.stream()
                    .filter(event -> "http.request.unhandled_exception"
                            .equals(keyValueMap(event).get("event")))
                    .findFirst()
                    .orElseThrow();
            assertThat(errorEvent.getLevel()).isSameAs(Level.ERROR);
            assertThat(errorEvent.getMDCPropertyMap()).containsEntry("request_id", "req-filter-500");
            assertThat(errorEvent.getThrowableProxy()).isNull();
            assertThat(errorEvent.getFormattedMessage()).doesNotContain("secret-filter-detail");
            assertThat(keyValueMap(errorEvent))
                    .containsEntry("error_code", "INTERNAL_ERROR")
                    .containsEntry("status", 500)
                    .containsEntry("exception_type", IllegalStateException.class.getName());

            ILoggingEvent completionEvent = appender.list.stream()
                    .filter(event -> "http.request.completed".equals(keyValueMap(event).get("event")))
                    .findFirst()
                    .orElseThrow();
            assertThat(completionEvent.getMDCPropertyMap()).containsEntry("request_id", "req-filter-500");
            assertThat(keyValueMap(completionEvent)).containsEntry("status", 500);
            assertThat(errorTracker.capturedException).isInstanceOf(IllegalStateException.class);
            assertThat(errorTracker.capturedEventName).isEqualTo("http.request.unhandled_exception");
            assertThat(errorTracker.capturedErrorCode.getCode()).isEqualTo("INTERNAL_ERROR");
            assertThat(errorTracker.capturedRequest).isSameAs(request);
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
            MDC.remove(RequestIdFilter.REQUEST_ID_MDC_KEY);
        }
    }

    private Map<String, Object> keyValueMap(ILoggingEvent event) {
        return event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(keyValuePair -> keyValuePair.key, keyValuePair -> keyValuePair.value));
    }

    private static class RecordingErrorTracker implements ErrorTracker {

        private String capturedEventName;
        private ErrorCode capturedErrorCode;
        private Throwable capturedException;
        private jakarta.servlet.http.HttpServletRequest capturedRequest;
        private jakarta.servlet.http.HttpServletRequest capturedClientErrorRequest;
        private Integer capturedClientErrorStatus;

        @Override
        public void capture(
                String eventName,
                ErrorCode errorCode,
                Throwable exception,
                jakarta.servlet.http.HttpServletRequest request
        ) {
            this.capturedEventName = eventName;
            this.capturedErrorCode = errorCode;
            this.capturedException = exception;
            this.capturedRequest = request;
        }

        @Override
        public void captureUnexpectedClientError(jakarta.servlet.http.HttpServletRequest request, int status) {
            this.capturedClientErrorRequest = request;
            this.capturedClientErrorStatus = status;
        }
    }
}
