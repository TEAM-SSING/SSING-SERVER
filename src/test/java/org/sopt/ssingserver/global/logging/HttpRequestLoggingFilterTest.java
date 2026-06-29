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
            MDC.put(TraceIdFilter.REQUEST_ID_MDC_KEY, "req-123");
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/songs");
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
                    .containsEntry("path", "/api/songs")
                    .containsEntry("status", 201);
            assertThat(keyValueMap(event).get("duration_ms")).isInstanceOf(Long.class);
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
            MDC.remove(TraceIdFilter.REQUEST_ID_MDC_KEY);
        }
    }

    private Map<String, Object> keyValueMap(ILoggingEvent event) {
        return event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(keyValuePair -> keyValuePair.key, keyValuePair -> keyValuePair.value));
    }
}
