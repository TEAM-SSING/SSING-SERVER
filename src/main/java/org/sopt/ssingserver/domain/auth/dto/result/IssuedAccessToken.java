package org.sopt.ssingserver.domain.auth.dto.result;

public record IssuedAccessToken(
        String accessToken,
        String tokenType,
        long expiresIn
) {

    public static IssuedAccessToken of(
            String accessToken,
            String tokenType,
            long expiresIn
    ) {
        return new IssuedAccessToken(accessToken, tokenType, expiresIn);
    }
}
