package org.sopt.ssingserver.global.logging;

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
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
            MockHttpServletResponse response = new MockHttpServletResponse();

            new HttpRequestLoggingFilter().doFilter(request, response, (servletRequest, servletResponse) ->
                    ((MockHttpServletResponse) servletResponse).setStatus(200));

            assertThat(appender.list).isEmpty();
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void 비정상_health_응답은_완료_로그를_남긴다() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(HttpRequestLoggingFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
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
        }
    }

    @Test
    void 매핑_템플릿이_없으면_raw_URI_대신_unmapped를_기록한다() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(HttpRequestLoggingFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/unknown/private-value");
            MockHttpServletResponse response = new MockHttpServletResponse();

            new HttpRequestLoggingFilter().doFilter(request, response, (servletRequest, servletResponse) ->
                    ((MockHttpServletResponse) servletResponse).setStatus(404));

            assertThat(keyValueMap(appender.list.getFirst()))
                    .containsEntry("path", "/unmapped")
                    .doesNotContainValue("/unknown/private-value");
        } finally {
            logger.detachAppender(appender);
        }
    }

    private Map<String, Object> keyValueMap(ILoggingEvent event) {
        return event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(keyValuePair -> keyValuePair.key, keyValuePair -> keyValuePair.value));
    }
}
