package org.sopt.ssingserver.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record AuthRefreshRequest(
        @Schema(description = "SSING Refresh Token", example = "dummy_refresh_token")
        @NotBlank(message = "Refresh Token은 필수입니다.")
        String refreshToken
) {
}
