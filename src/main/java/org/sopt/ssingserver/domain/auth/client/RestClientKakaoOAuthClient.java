package org.sopt.ssingserver.domain.auth.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
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

    private static final String BEARER_PREFIX = "Bearer ";
    private static final int KAKAO_INVALID_PARAMETER_CODE = -2;
    private static final int KAKAO_INVALID_TOKEN_CODE = -401;
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

    private SimpleClientHttpRequestFactory createRequestFactory(KakaoOAuthProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return requestFactory;
    }

    @Override
    public KakaoTokenInfo validateAccessToken(String kakaoAccessToken) {
        KakaoTokenInfoResponse tokenInfoResponse = requestTokenInfo(kakaoAccessToken);
        String expectedAppId = normalizeAppId(properties.appId());
        String actualAppId = tokenInfoResponse == null ? null : normalizeAppId(tokenInfoResponse.appId());
        // SSING 카카오 앱 식별자 검증
        if (tokenInfoResponse == null
                || tokenInfoResponse.id() == null
                || expectedAppId == null
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

    private String normalizeAppId(String appId) {
        if (!StringUtils.hasText(appId)) {
            return null;
        }
        return appId.trim();
    }

    private KakaoTokenInfoResponse requestTokenInfo(String kakaoAccessToken) {
        try {
            // 카카오 토큰 유효성 검증용 경량 API
            return restClient.get()
                    .uri("/v1/user/access_token_info")
                    .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + kakaoAccessToken)
                    .retrieve()
                    .body(KakaoTokenInfoResponse.class);
        } catch (ResourceAccessException exception) {
            throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, exception);
        } catch (RestClientResponseException exception) {
            throw mapKakaoResponseException(exception);
        } catch (RestClientException exception) {
            throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, exception);
        }
    }

    private KakaoUserMeResponse requestUserMe(String kakaoAccessToken) {
        try {
            // 신규 회원 생성용 카카오 프로필 조회
            return restClient.get()
                    .uri("/v2/user/me")
                    .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + kakaoAccessToken)
                    .retrieve()
                    .body(KakaoUserMeResponse.class);
        } catch (ResourceAccessException exception) {
            throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, exception);
        } catch (RestClientResponseException exception) {
            throw mapKakaoResponseException(exception);
        } catch (RestClientException exception) {
            throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, exception);
        }
    }

    private BusinessException mapKakaoResponseException(RestClientResponseException exception) {
        if (isInvalidKakaoToken(exception)) {
            return new BusinessException(AuthErrorCode.AUTH_INVALID_KAKAO_TOKEN, exception);
        }
        return new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, exception);
    }

    private boolean isInvalidKakaoToken(RestClientResponseException exception) {
        if (exception.getStatusCode().value() == 401) {
            return true;
        }

        KakaoErrorResponse errorResponse = readKakaoErrorResponse(exception);
        if (errorResponse == null || errorResponse.code() == null) {
            return false;
        }

        return errorResponse.code() == KAKAO_INVALID_TOKEN_CODE
                || errorResponse.code() == KAKAO_INVALID_PARAMETER_CODE;
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
            String appId
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
