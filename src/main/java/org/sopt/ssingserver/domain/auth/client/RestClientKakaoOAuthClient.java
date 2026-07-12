package org.sopt.ssingserver.domain.auth.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sopt.ssingserver.domain.auth.config.KakaoOAuthProperties;
import org.sopt.ssingserver.domain.auth.error.AuthErrorCode;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class RestClientKakaoOAuthClient implements KakaoOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(RestClientKakaoOAuthClient.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int KAKAO_INVALID_TOKEN_CODE = -401;
    private static final String VALIDATE_ACCESS_TOKEN_OPERATION = "validate_access_token";
    private static final String GET_PROFILE_OPERATION = "get_profile";
    private final RestClient restClient;
    private final KakaoOAuthProperties properties;
    private final ObjectMapper objectMapper;

    public RestClientKakaoOAuthClient(
            RestClient.Builder restClientBuilder,
            KakaoOAuthProperties properties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .requestFactory(createRequestFactory(properties))
                .build();
    }

    @Override
    public KakaoTokenInfo validateAccessToken(String kakaoAccessToken) {
        KakaoTokenInfoResponse tokenInfoResponse = requestTokenInfo(kakaoAccessToken);
        Integer expectedAppId = properties.appId();
        Integer actualAppId = tokenInfoResponse == null ? null : tokenInfoResponse.appId();
        // SSING 카카오 앱 식별자 검증
        if (tokenInfoResponse == null
                || tokenInfoResponse.id() == null
                || actualAppId == null
                || !expectedAppId.equals(actualAppId)) {
            throw new BusinessException(AuthErrorCode.AUTH_INVALID_KAKAO_TOKEN);
        }
        return new KakaoTokenInfo(String.valueOf(tokenInfoResponse.id()));
    }

    @Override
    public KakaoProfile getProfile(String kakaoAccessToken) {
        KakaoUserMeResponse userMeResponse = requestUserMe(kakaoAccessToken);
        if (userMeResponse == null || userMeResponse.id() == null) {
            throw new BusinessException(AuthErrorCode.AUTH_INVALID_KAKAO_TOKEN);
        }
        return new KakaoProfile(
                String.valueOf(userMeResponse.id()),
                resolveNickname(userMeResponse),
                resolveProfileImageUrl(userMeResponse)
        );
    }

    private SimpleClientHttpRequestFactory createRequestFactory(KakaoOAuthProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return requestFactory;
    }

    private KakaoTokenInfoResponse requestTokenInfo(String kakaoAccessToken) {
        long startedAt = System.nanoTime();
        try {
            // 카카오 토큰 유효성 검증용 경량 API
            return restClient.get()
                    .uri("/v1/user/access_token_info")
                    .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + kakaoAccessToken)
                    .retrieve()
                    .body(KakaoTokenInfoResponse.class);
        } catch (ResourceAccessException exception) {
            logExternalFailure(VALIDATE_ACCESS_TOKEN_OPERATION, startedAt, exception, null);
            throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, exception);
        } catch (RestClientResponseException exception) {
            throw mapKakaoResponseException(VALIDATE_ACCESS_TOKEN_OPERATION, startedAt, exception);
        } catch (RestClientException exception) {
            logExternalFailure(VALIDATE_ACCESS_TOKEN_OPERATION, startedAt, exception, null);
            throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, exception);
        }
    }

    private KakaoUserMeResponse requestUserMe(String kakaoAccessToken) {
        long startedAt = System.nanoTime();
        try {
            // 신규 회원 생성용 카카오 프로필 조회
            return restClient.get()
                    .uri("/v2/user/me")
                    .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + kakaoAccessToken)
                    .retrieve()
                    .body(KakaoUserMeResponse.class);
        } catch (ResourceAccessException exception) {
            logExternalFailure(GET_PROFILE_OPERATION, startedAt, exception, null);
            throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, exception);
        } catch (RestClientResponseException exception) {
            throw mapKakaoResponseException(GET_PROFILE_OPERATION, startedAt, exception);
        } catch (RestClientException exception) {
            logExternalFailure(GET_PROFILE_OPERATION, startedAt, exception, null);
            throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, exception);
        }
    }

    private BusinessException mapKakaoResponseException(
            String operation,
            long startedAt,
            RestClientResponseException exception
    ) {
        if (isInvalidKakaoToken(exception)) {
            return new BusinessException(AuthErrorCode.AUTH_INVALID_KAKAO_TOKEN, exception);
        }
        logExternalFailure(operation, startedAt, exception, exception.getStatusCode().value());
        return new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, exception);
    }

    private void logExternalFailure(
            String operation,
            long startedAt,
            RestClientException exception,
            Integer providerStatus
    ) {
        // 외부 연동 최종 실패는 client 경계에서 한 번만 기록하며 토큰과 provider 응답은 제외한다.
        var eventBuilder = log.atError()
                .addKeyValue("event", "external.kakao.request.failed")
                .addKeyValue("provider", "kakao")
                .addKeyValue("operation", operation)
                .addKeyValue("error_code", CommonErrorCode.EXTERNAL_SERVICE_UNAVAILABLE.getCode())
                .addKeyValue("duration_ms", (System.nanoTime() - startedAt) / 1_000_000L)
                .addKeyValue("exception_type", exception.getClass().getName());
        if (providerStatus != null) {
            eventBuilder.addKeyValue("provider_status", providerStatus);
        }
        eventBuilder.log("Kakao request failed");
    }

    private boolean isInvalidKakaoToken(RestClientResponseException exception) {
        if (exception.getStatusCode().value() == 401) {
            return true;
        }

        KakaoErrorResponse errorResponse = readKakaoErrorResponse(exception);
        if (errorResponse == null || errorResponse.code() == null) {
            return false;
        }

        return errorResponse.code() == KAKAO_INVALID_TOKEN_CODE;
    }

    private KakaoErrorResponse readKakaoErrorResponse(RestClientResponseException exception) {
        String responseBody = exception.getResponseBodyAsString();
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }

        try {
            return objectMapper.readValue(responseBody, KakaoErrorResponse.class);
        } catch (JacksonException parseException) {
            return null;
        }
    }

    private String resolveNickname(KakaoUserMeResponse userMeResponse) {
        KakaoProfileResponse profile = userMeResponse.profile();
        if (profile != null && StringUtils.hasText(profile.nickname())) {
            return profile.nickname();
        }

        Map<String, String> properties = userMeResponse.properties();
        if (properties != null && StringUtils.hasText(properties.get("nickname"))) {
            return properties.get("nickname");
        }
        return null;
    }

    private String resolveProfileImageUrl(KakaoUserMeResponse userMeResponse) {
        KakaoProfileResponse profile = userMeResponse.profile();
        if (profile != null && StringUtils.hasText(profile.profileImageUrl())) {
            return profile.profileImageUrl();
        }

        Map<String, String> properties = userMeResponse.properties();
        if (properties == null) {
            return null;
        }
        return properties.get("profile_image");
    }

    private record KakaoErrorResponse(
            Integer code,
            String msg
    ) {
    }

    private record KakaoTokenInfoResponse(
            Long id,
            @JsonProperty("app_id")
            Integer appId
    ) {
    }

    private record KakaoUserMeResponse(
            Long id,
            @JsonProperty("kakao_account")
            KakaoAccountResponse kakaoAccount,
            Map<String, String> properties
    ) {

        private KakaoProfileResponse profile() {
            return kakaoAccount == null ? null : kakaoAccount.profile();
        }
    }

    private record KakaoAccountResponse(
            KakaoProfileResponse profile
    ) {
    }

    private record KakaoProfileResponse(
            String nickname,
            @JsonProperty("profile_image_url")
            String profileImageUrl
    ) {
    }
}
