package org.sopt.ssingserver.domain.auth.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import org.sopt.ssingserver.domain.auth.config.KakaoOAuthProperties;
import org.sopt.ssingserver.domain.auth.error.AuthErrorCode;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class RestClientKakaoOAuthClient implements KakaoOAuthClient {

    private static final String BEARER_PREFIX = "Bearer ";
    private final RestClient restClient;
    private final KakaoOAuthProperties properties;

    public RestClientKakaoOAuthClient(
            RestClient.Builder restClientBuilder,
            KakaoOAuthProperties properties
    ) {
        this.properties = properties;
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
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        throw new BusinessException(AuthErrorCode.AUTH_INVALID_KAKAO_TOKEN);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                        throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
                    })
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
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        throw new BusinessException(AuthErrorCode.AUTH_INVALID_KAKAO_TOKEN);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                        throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
                    })
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
        if (exception.getStatusCode().is4xxClientError()) {
            return new BusinessException(AuthErrorCode.AUTH_INVALID_KAKAO_TOKEN, exception);
        }
        return new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, exception);
    }

    private String resolveNickname(KakaoUserMeResponse userMeResponse) {
        Map<String, String> properties = userMeResponse.properties();
        if (properties != null && StringUtils.hasText(properties.get("nickname"))) {
            return properties.get("nickname");
        }
        return null;
    }

    private String resolveProfileImageUrl(KakaoUserMeResponse userMeResponse) {
        Map<String, String> properties = userMeResponse.properties();
        if (properties == null) {
            return null;
        }
        return properties.get("profile_image");
    }

    private record KakaoTokenInfoResponse(
            Long id,
            @JsonProperty("app_id")
            String appId
    ) {
    }

    private record KakaoUserMeResponse(
            Long id,
            Map<String, String> properties
    ) {
    }
}
