package org.sopt.ssingserver.domain.auth.dev.dto.request;

import jakarta.validation.constraints.NotBlank;

public record DevAuthTokenRequest(
        @NotBlank(message = "personaKey는 필수입니다.")
        String personaKey
) {
}
