package org.sopt.ssingserver.domain.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ssing.auth.refresh-token")
public record RefreshTokenProperties(
        Duration expiration
) {

    public RefreshTokenProperties {
        if (expiration == null || expiration.isNegative() || expiration.isZero()) {
            throw new IllegalArgumentException("Refresh token expiration must be positive.");
        }
    }
}
