package org.sopt.ssingserver.domain.auth.service;

public record IssuedAccessToken(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
