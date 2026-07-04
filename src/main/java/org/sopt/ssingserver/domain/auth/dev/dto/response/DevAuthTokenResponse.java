package org.sopt.ssingserver.domain.auth.dev.dto.response;

import org.sopt.ssingserver.domain.auth.dto.result.IssuedAuthTokens;

public record DevAuthTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        DevPersonaSnapshotResponse persona
) {

    public static DevAuthTokenResponse from(
            IssuedAuthTokens tokens,
            DevPersonaResponse persona
    ) {
        return new DevAuthTokenResponse(
                tokens.accessToken(),
                tokens.refreshToken(),
                tokens.tokenType(),
                tokens.expiresIn(),
                DevPersonaSnapshotResponse.from(persona)
        );
    }
}
