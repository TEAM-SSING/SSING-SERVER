package org.sopt.ssingserver.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record KakaoLoginRequest(
        @Schema(
                description = "카카오에서 발급받은 Access Token",
                example = "dummy_kakao_access_token"
        )
        @NotBlank(message = "카카오 Access Token은 필수입니다.")
        String kakaoAccessToken
) {
}
