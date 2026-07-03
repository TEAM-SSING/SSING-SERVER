package org.sopt.ssingserver.domain.auth.dto.response;

public record AuthRefreshResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
