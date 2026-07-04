package org.sopt.ssingserver.domain.auth.dto.result;

public record IssuedAuthTokens(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {

    public static IssuedAuthTokens of(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn
    ) {
        return new IssuedAuthTokens(accessToken, refreshToken, tokenType, expiresIn);
    }
}
