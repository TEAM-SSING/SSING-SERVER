package org.sopt.ssingserver.domain.auth.dev.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.sopt.ssingserver.domain.auth.dto.result.IssuedAuthTokens;

@Schema(description = "개발용 persona 토큰 발급 결과")
public record DevAuthTokenResponse(
        @Schema(description = "합성 persona의 SSING Access Token", example = "dummy_access_token")
        String accessToken,

        @Schema(description = "합성 persona의 SSING Refresh Token", example = "dummy_refresh_token")
        String refreshToken,

        @Schema(description = "토큰 인증 타입", example = "Bearer")
        String tokenType,

        @Schema(description = "Access Token 만료까지 남은 초", example = "3600")
        long expiresIn,

        @Schema(description = "토큰을 발급한 persona 상태 스냅샷")
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
