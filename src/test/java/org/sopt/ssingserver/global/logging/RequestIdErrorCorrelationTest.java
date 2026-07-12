package org.sopt.ssingserver.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.sopt.ssingserver.global.error.ErrorResponseFactory;
import org.sopt.ssingserver.global.error.GlobalExceptionHandler;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class RequestIdErrorCorrelationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 내부_5xx_응답과_ERROR_로그는_같은_request_id를_사용한다() throws Exception {
        Logger exceptionHandlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        Logger httpLoggingFilterLogger = (Logger) LoggerFactory.getLogger(HttpRequestLoggingFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        exceptionHandlerLogger.addAppender(appender);
        httpLoggingFilterLogger.addAppender(appender);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler(new ErrorResponseFactory()))
                .addFilters(new RequestIdFilter(), new HttpRequestLoggingFilter())
                .build();

        try {
            MvcResult result = mockMvc.perform(get("/test/failure")
                            .header(RequestIdFilter.REQUEST_ID_HEADER, "req-integration-500"))
                    .andExpect(status().isInternalServerError())
                    .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(response.path("requestId").asString()).isEqualTo("req-integration-500");

            List<ILoggingEvent> errorEvents = appender.list.stream()
                    .filter(event -> "http.request.unhandled_exception"
                            .equals(keyValueMap(event).get("event")))
                    .toList();
            assertThat(errorEvents).hasSize(1);
            assertThat(errorEvents.getFirst().getMDCPropertyMap())
                    .containsEntry("request_id", "req-integration-500");
            assertThat(errorEvents.getFirst().getThrowableProxy()).isNull();
        } finally {
            exceptionHandlerLogger.detachAppender(appender);
            httpLoggingFilterLogger.detachAppender(appender);
        }
    }

    private Map<String, Object> keyValueMap(ILoggingEvent event) {
        return event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(keyValuePair -> keyValuePair.key, keyValuePair -> keyValuePair.value));
    }

    @RestController
    private static class FailingController {

        @GetMapping("/test/failure")
        void fail() {
            throw new IllegalStateException("secret-controller-detail");
        }
    }
}
