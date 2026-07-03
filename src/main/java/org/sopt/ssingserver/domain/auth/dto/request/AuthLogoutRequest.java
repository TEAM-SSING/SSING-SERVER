package org.sopt.ssingserver.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AuthLogoutRequest(
        @NotBlank(message = "Refresh Token은 필수입니다.")
        String refreshToken
) {
}
