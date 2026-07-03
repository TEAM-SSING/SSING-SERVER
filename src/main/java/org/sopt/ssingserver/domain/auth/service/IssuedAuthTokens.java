package org.sopt.ssingserver.domain.auth.service;

public record IssuedAuthTokens(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
}
