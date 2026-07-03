package org.sopt.ssingserver.domain.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ssing.auth.kakao")
public record KakaoOAuthProperties(
        String baseUrl,
        String appId,
        Duration connectTimeout,
        Duration readTimeout
) {

    public KakaoOAuthProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://kapi.kakao.com";
        }
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("Kakao app id must not be blank.");
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(2);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(3);
        }
    }
}
