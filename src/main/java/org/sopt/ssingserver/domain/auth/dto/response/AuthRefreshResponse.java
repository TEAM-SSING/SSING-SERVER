package org.sopt.ssingserver.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Access Token 재발급 결과")
public record AuthRefreshResponse(
        @Schema(description = "새로 발급한 SSING Access Token", example = "dummy_access_token")
        String accessToken,

        @Schema(description = "토큰 인증 타입", example = "Bearer")
        String tokenType,

        @Schema(description = "Access Token 만료까지 남은 초", example = "3600")
        long expiresIn
) {
}
