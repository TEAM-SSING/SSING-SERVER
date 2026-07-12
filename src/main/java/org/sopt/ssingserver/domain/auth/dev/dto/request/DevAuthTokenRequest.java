package org.sopt.ssingserver.domain.auth.dev.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record DevAuthTokenRequest(
        @Schema(description = "토큰을 발급할 개발용 persona key", example = "consumer-matching-a")
        @NotBlank(message = "personaKey는 필수입니다.")
        String personaKey
) {
}
