package org.sopt.ssingserver.global.security;

import org.sopt.ssingserver.domain.auth.error.AuthErrorCode;
import org.sopt.ssingserver.domain.auth.token.AccessTokenException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AuthTokenExtractor {

    private static final String BEARER_PREFIX = "Bearer ";

    public String extractBearerToken(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            throw new AccessTokenException(AuthErrorCode.AUTH_INVALID_TOKEN);
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            throw new AccessTokenException(AuthErrorCode.AUTH_INVALID_TOKEN);
        }
        return token;
    }

    public String extractNullableBearerToken(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return null;
        }
        return extractBearerToken(authorization);
    }
}
