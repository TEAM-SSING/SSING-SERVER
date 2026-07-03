package org.sopt.ssingserver.domain.auth.dev.dto.response;

public record DevAuthTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        DevPersonaSnapshotResponse persona,
        DevMetaResponse devMeta
) {
}
