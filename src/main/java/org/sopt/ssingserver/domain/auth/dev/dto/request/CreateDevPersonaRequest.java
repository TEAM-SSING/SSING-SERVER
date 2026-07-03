package org.sopt.ssingserver.domain.auth.dev.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateDevPersonaRequest(
        @NotBlank(message = "personaKey는 필수입니다.")
        String personaKey,

        @NotBlank(message = "nickname은 필수입니다.")
        String nickname,

        @NotBlank(message = "template은 필수입니다.")
        String template
) {
}
