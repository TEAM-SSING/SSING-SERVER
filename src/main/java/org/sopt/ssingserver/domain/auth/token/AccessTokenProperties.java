package org.sopt.ssingserver.domain.auth.token;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ssing.auth.jwt")
public record AccessTokenProperties(
        String issuer,
        String secret,
        Duration accessTokenExpiration
) {

    private static final int MIN_SECRET_LENGTH = 32;

    public AccessTokenProperties {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("JWT issuer must not be blank.");
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret must not be blank.");
        }
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters.");
        }
        if (accessTokenExpiration == null || accessTokenExpiration.isNegative() || accessTokenExpiration.isZero()) {
            throw new IllegalArgumentException("Access token expiration must be positive.");
        }
    }
}
