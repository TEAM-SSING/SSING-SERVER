package org.sopt.ssingserver.domain.auth.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.sopt.ssingserver.domain.auth.config.KakaoOAuthProperties;
import org.sopt.ssingserver.domain.auth.error.AuthErrorCode;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

class RestClientKakaoOAuthClientTest {

    private static final String SECRET_TOKEN = "secret-kakao-token";

    @Test
    void 카카오_토큰_거절은_ERROR를_남기지_않는다() {
        RestClientResponseException unauthorized = new RestClientResponseException(
                "Unauthorized secret-response",
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                HttpHeaders.EMPTY,
                "{\"code\":-401,\"msg\":\"secret-response\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
        RestClient restClient = restClientThrowing(unauthorized);
        RestClientKakaoOAuthClient client = createClient(restClient);
        Logger logger = (Logger) LoggerFactory.getLogger(RestClientKakaoOAuthClient.class);
        ListAppender<ILoggingEvent> appender = attachAppender(logger);

        try {
            assertThatExceptionOfType(BusinessException.class)
                    .isThrownBy(() -> client.validateAccessToken(SECRET_TOKEN))
                    .satisfies(exception -> assertThat(exception.getErrorCode())
                            .isSameAs(AuthErrorCode.AUTH_INVALID_KAKAO_TOKEN));

            assertThat(appender.list).isEmpty();
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void 카카오_timeout은_민감정보_없이_구조화_ERROR를_한_번_남긴다() {
        RestClient restClient = restClientThrowing(new ResourceAccessException("timeout with " + SECRET_TOKEN));
        RestClientKakaoOAuthClient client = createClient(restClient);
        Logger logger = (Logger) LoggerFactory.getLogger(RestClientKakaoOAuthClient.class);
        ListAppender<ILoggingEvent> appender = attachAppender(logger);

        try {
            assertThatExceptionOfType(BusinessException.class)
                    .isThrownBy(() -> client.validateAccessToken(SECRET_TOKEN))
                    .satisfies(exception -> assertThat(exception.getErrorCode())
                            .isSameAs(CommonErrorCode.EXTERNAL_SERVICE_UNAVAILABLE));

            assertThat(appender.list).hasSize(1);
            ILoggingEvent event = appender.list.getFirst();
            assertThat(event.getLevel()).isSameAs(Level.ERROR);
            assertThat(event.getThrowableProxy()).isNull();
            assertThat(event.getFormattedMessage()).doesNotContain(SECRET_TOKEN, "timeout");
            assertThat(keyValueMap(event))
                    .containsEntry("event", "external.kakao.request.failed")
                    .containsEntry("provider", "kakao")
                    .containsEntry("operation", "validate_access_token")
                    .containsEntry("error_code", "EXTERNAL_SERVICE_UNAVAILABLE")
                    .containsEntry("exception_type", ResourceAccessException.class.getName());
            assertThat(keyValueMap(event).get("duration_ms")).isInstanceOf(Long.class);
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void 카카오_provider_5xx는_응답본문_없이_상태코드만_기록한다() {
        RestClientResponseException providerFailure = new RestClientResponseException(
                "Provider failure secret-response",
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                HttpHeaders.EMPTY,
                "secret-response-body".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
        RestClientKakaoOAuthClient client = createClient(restClientThrowing(providerFailure));
        Logger logger = (Logger) LoggerFactory.getLogger(RestClientKakaoOAuthClient.class);
        ListAppender<ILoggingEvent> appender = attachAppender(logger);

        try {
            assertThatExceptionOfType(BusinessException.class)
                    .isThrownBy(() -> client.getProfile(SECRET_TOKEN))
                    .satisfies(exception -> assertThat(exception.getErrorCode())
                            .isSameAs(CommonErrorCode.EXTERNAL_SERVICE_UNAVAILABLE));

            assertThat(appender.list).hasSize(1);
            ILoggingEvent event = appender.list.getFirst();
            assertThat(event.getThrowableProxy()).isNull();
            assertThat(event.getFormattedMessage()).doesNotContain("secret-response", SECRET_TOKEN);
            assertThat(keyValueMap(event))
                    .containsEntry("operation", "get_profile")
                    .containsEntry("provider_status", 500)
                    .containsEntry("exception_type", RestClientResponseException.class.getName());
        } finally {
            logger.detachAppender(appender);
        }
    }

    private RestClientKakaoOAuthClient createClient(RestClient restClient) {
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
