package org.sopt.ssingserver.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.sopt.ssingserver.domain.auth.client.RestClientKakaoOAuthClient;
import org.sopt.ssingserver.domain.auth.config.KakaoOAuthProperties;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.repository.MatchingOfferRepository;
import org.sopt.ssingserver.domain.matching.service.MatchingOfferExpirationService;
import org.sopt.ssingserver.domain.matching.service.MatchingOfferExpirationTriggerService;
import org.sopt.ssingserver.domain.matching.service.MatchingTimeoutPolicy;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.ErrorResponseFactory;
import org.sopt.ssingserver.global.error.GlobalExceptionHandler;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.HandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DevJsonLoggingContractTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-12T00:00:00Z"),
            ZoneOffset.UTC
    );
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final List<String> encodedLines = new ArrayList<>();
    private LogbackLoggingSystem loggingSystem;

    @BeforeAll
    void dev_로그백_설정을_적용한다() {
        configureLogging("dev");
    }

    @AfterAll
    void 실제_dev_JSON_표본을_저장하고_test_로그백_설정으로_복구한다() throws Exception {
        try {
            Path output = Path.of("build", "logging-contract", "dev-json-samples.jsonl");
            Files.createDirectories(output.getParent());
            Files.write(output, encodedLines, StandardCharsets.UTF_8);
        } finally {
            loggingSystem.cleanUp();
            configureLogging("test");
        }
    }

    @Test
    void dev_JSON은_HTTP_완료_필드와_숫자_타입을_보존한다() throws Exception {
        MDC.put(RequestIdFilter.REQUEST_ID_MDC_KEY, "req-json-http");
        try {
            ILoggingEvent event = captureSingleEvent(HttpRequestLoggingFilter.class, () -> {
                MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test/42");
                request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/test/{testId}");
                MockHttpServletResponse response = new MockHttpServletResponse();
                try {
                    new HttpRequestLoggingFilter().doFilter(request, response, (servletRequest, servletResponse) ->
                            ((MockHttpServletResponse) servletResponse).setStatus(200));
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });

            JsonNode json = encode(event);

            assertBaseFields(json);
            assertThat(json.path("event").asString()).isEqualTo("http.request.completed");
            assertThat(json.path("request_id").asString()).isEqualTo("req-json-http");
            assertThat(json.path("method").asString()).isEqualTo("GET");
            assertThat(json.path("path").asString()).isEqualTo("/api/v1/test/{testId}");
            assertThat(json.path("status").isIntegralNumber()).isTrue();
            assertThat(json.path("duration_ms").isIntegralNumber()).isTrue();
        } finally {
            MDC.remove(RequestIdFilter.REQUEST_ID_MDC_KEY);
        }
    }

    @Test
    void dev_JSON은_내부_5xx를_request_id와_안전한_필드로_기록한다() throws Exception {
        MDC.put(RequestIdFilter.REQUEST_ID_MDC_KEY, "req-json-500");
        try {
            ILoggingEvent event = captureSingleEvent(GlobalExceptionHandler.class, () -> {
                MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
                request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "req-json-500");
                new GlobalExceptionHandler(new ErrorResponseFactory())
                        .handleException(new IllegalStateException("secret-detail"), request);
            });

            JsonNode json = encode(event);

            assertBaseFields(json);
            assertThat(json.path("event").asString()).isEqualTo("http.request.unhandled_exception");
            assertThat(json.path("request_id").asString()).isEqualTo("req-json-500");
            assertThat(json.path("status").intValue()).isEqualTo(500);
            assertThat(json.path("error_code").asString()).isEqualTo("INTERNAL_ERROR");
            assertThat(json.path("exception_type").asString())
                    .isEqualTo(IllegalStateException.class.getName());
            assertThat(json.has("stack_trace")).isFalse();
            assertThat(json.toString()).doesNotContain("secret-detail");
        } finally {
            MDC.remove(RequestIdFilter.REQUEST_ID_MDC_KEY);
        }
    }

    @Test
    void dev_JSON은_Kakao_최종_장애에서_token과_raw_cause를_제외한다() throws Exception {
        RestClientKakaoOAuthClient client = kakaoClientThrowing(
                new ResourceAccessException("timeout with secret-kakao-token")
        );
        MDC.put(RequestIdFilter.REQUEST_ID_MDC_KEY, "req-json-kakao");
        try {
            List<ILoggingEvent> events = captureEvents(RestClientKakaoOAuthClient.class, () ->
                    assertThatExceptionOfType(BusinessException.class)
                            .isThrownBy(() -> client.validateAccessToken("secret-kakao-token"))
            );

            assertThat(events).hasSize(1);
            JsonNode json = encode(events.getFirst());
            assertBaseFields(json);
            assertThat(json.path("event").asString()).isEqualTo("external.kakao.request.failed");
            assertThat(json.path("request_id").asString()).isEqualTo("req-json-kakao");
            assertThat(json.path("provider").asString()).isEqualTo("kakao");
            assertThat(json.path("operation").asString()).isEqualTo("validate_access_token");
            assertThat(json.path("duration_ms").isIntegralNumber()).isTrue();
            assertThat(json.has("stack_trace")).isFalse();
            assertThat(json.toString()).doesNotContain("secret-kakao-token", "timeout with");
        } finally {
            MDC.remove(RequestIdFilter.REQUEST_ID_MDC_KEY);
        }
    }

    @Test
    void dev_JSON은_배치_부분실패의_job_run_id와_count_타입을_보존한다() throws Exception {
        MatchingOfferRepository repository = mock(MatchingOfferRepository.class);
        MatchingOfferExpirationService expirationService = mock(MatchingOfferExpirationService.class);
        MatchingTimeoutPolicy timeoutPolicy = mock(MatchingTimeoutPolicy.class);
        when(timeoutPolicy.shouldRunInstructorOfferExpiration()).thenReturn(true);
        when(repository.findIdsByStatusAndExpiresAtLessThanEqualOrderByIdAsc(
                MatchingOfferStatus.OFFERED,
                FIXED_CLOCK.instant(),
                PageRequest.of(0, 100)
        )).thenReturn(List.of(1L, 2L));
        doAnswer(invocation -> {
            if (invocation.getArgument(0, Long.class).equals(2L)) {
                throw new IllegalStateException("secret-batch-detail");
            }
            return null;
        }).when(expirationService).expireOffer(anyLong());
        MatchingOfferExpirationTriggerService service = new MatchingOfferExpirationTriggerService(
                repository,
                expirationService,
                timeoutPolicy,
                FIXED_CLOCK
        );

        List<ILoggingEvent> events = captureEvents(MatchingOfferExpirationTriggerService.class, service::expireOverdueOffers);

        assertThat(events).hasSize(2);
        JsonNode detail = encode(events.getFirst());
        JsonNode summary = encode(events.getLast());
        assertBaseFields(detail);
        assertThat(detail.path("event").asString())
                .isEqualTo("matching.offer.expiration.failed");
        assertBaseFields(summary);
        assertThat(summary.path("event").asString())
                .isEqualTo("matching.offer.expiration.batch.completed");
        assertThat(summary.path("job_status").asString()).isEqualTo("partial_failure");
        assertThat(summary.path("processed_count").isIntegralNumber()).isTrue();
        assertThat(summary.path("success_count").intValue()).isEqualTo(1);
        assertThat(summary.path("failure_count").intValue()).isEqualTo(1);
        assertThat(summary.path("duration_ms").isIntegralNumber()).isTrue();
        assertThat(summary.path("job_run_id").asString()).isNotBlank();
        assertThat(detail.path("job_run_id").asString()).isEqualTo(summary.path("job_run_id").asString());
        assertThat(detail.has("stack_trace")).isFalse();
        assertThat(detail.toString()).doesNotContain("secret-batch-detail");
    }

    @Test
    void prod_JSON도_dev와_같은_기본_schema를_사용한다() throws Exception {
        loggingSystem.cleanUp();
        configureLogging("prod");
        MDC.put(RequestIdFilter.REQUEST_ID_MDC_KEY, "req-json-prod");
        try {
            ILoggingEvent event = captureSingleEvent(HttpRequestLoggingFilter.class, () -> {
                MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test/42");
                request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/test/{testId}");
                MockHttpServletResponse response = new MockHttpServletResponse();
                try {
                    new HttpRequestLoggingFilter().doFilter(request, response, (servletRequest, servletResponse) ->
                            ((MockHttpServletResponse) servletResponse).setStatus(200));
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });

            JsonNode json = encode(event);

            assertBaseFields(json, "prod");
            assertThat(json.path("event").asString()).isEqualTo("http.request.completed");
            assertThat(json.path("request_id").asString()).isEqualTo("req-json-prod");
        } finally {
            MDC.remove(RequestIdFilter.REQUEST_ID_MDC_KEY);
            loggingSystem.cleanUp();
            configureLogging("dev");
        }
    }

    private void configureLogging(String profile) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles(profile);
        environment.getPropertySources().addFirst(new MapPropertySource(
                "logging-contract",
                Map.of(
                        "spring.application.name", "ssing-server",
                        "spring.profiles.active", profile
                )
        ));
        loggingSystem = new LogbackLoggingSystem(getClass().getClassLoader());
        loggingSystem.beforeInitialize();
        loggingSystem.getSystemProperties(environment).apply();
        loggingSystem.initialize(
                new LoggingInitializationContext(environment),
                "classpath:logback-spring.xml",
                null
        );
    }

    private ILoggingEvent captureSingleEvent(Class<?> loggerOwner, Runnable action) {
        List<ILoggingEvent> events = captureEvents(loggerOwner, action);
        assertThat(events).hasSize(1);
        return events.getFirst();
    }

    private List<ILoggingEvent> captureEvents(Class<?> loggerOwner, Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerOwner);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
            return List.copyOf(appender.list);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @SuppressWarnings("unchecked")
    private JsonNode encode(ILoggingEvent event) throws Exception {
        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Appender<ILoggingEvent> appender = rootLogger.getAppender("CONSOLE_JSON");
        assertThat(appender).isInstanceOf(OutputStreamAppender.class);
        Encoder<ILoggingEvent> encoder = ((OutputStreamAppender<ILoggingEvent>) appender).getEncoder();
        String json = new String(encoder.encode(event), StandardCharsets.UTF_8).trim();
        encodedLines.add(json);
        return OBJECT_MAPPER.readTree(json);
    }

    private void assertBaseFields(JsonNode json) {
        assertBaseFields(json, "dev");
    }

    private void assertBaseFields(JsonNode json, String environment) {
        assertThat(json.path("@timestamp").isString()).isTrue();
        assertThat(json.path("level").isString()).isTrue();
        assertThat(json.path("logger_name").isString()).isTrue();
        assertThat(json.path("thread_name").isString()).isTrue();
        assertThat(json.path("message").isString()).isTrue();
        assertThat(json.path("service").asString()).isEqualTo("ssing-server");
        assertThat(json.path("env").asString()).isEqualTo(environment);
    }

    private RestClientKakaoOAuthClient kakaoClientThrowing(RuntimeException exception) {
        RestClient restClient = restClientThrowing(exception);
        RestClient.Builder builder = mock(RestClient.Builder.class);
        when(builder.baseUrl(anyString())).thenReturn(builder);
        when(builder.requestFactory(any(ClientHttpRequestFactory.class))).thenReturn(builder);
        when(builder.build()).thenReturn(restClient);
        return new RestClientKakaoOAuthClient(
                builder,
                new KakaoOAuthProperties(
                        "https://kapi.kakao.test",
                        1234,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1)
                ),
                new ObjectMapper()
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private RestClient restClientThrowing(RuntimeException exception) {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec requestSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        when(restClient.get()).thenReturn(requestSpec);
        when(requestSpec.uri(anyString())).thenReturn(requestSpec);
        when(requestSpec.header(anyString(), any(String[].class))).thenReturn(requestSpec);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(Class.class))).thenThrow(exception);
        return restClient;
    }
}
