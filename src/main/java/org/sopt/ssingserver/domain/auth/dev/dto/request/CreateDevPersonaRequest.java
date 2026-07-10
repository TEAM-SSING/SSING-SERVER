package org.sopt.ssingserver.domain.auth.dev.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CreateDevPersonaRequest(
        @Schema(description = "중복되지 않는 개발용 persona key", example = "consumer-matching-a")
        @NotBlank(message = "personaKey는 필수입니다.")
        String personaKey,

        @Schema(description = "합성 회원 닉네임", example = "매칭 강습생 A")
        @NotBlank(message = "nickname은 필수입니다.")
        String nickname,

        @Schema(
                description = "생성할 회원·강사 상태 템플릿",
                example = "GENERAL_CONSUMER",
                allowableValues = {
                        "GENERAL_CONSUMER",
                        "SUSPENDED_CONSUMER",
                        "INSTRUCTOR_PENDING",
                        "INSTRUCTOR_APPROVED"
                }
        )
        @NotBlank(message = "template은 필수입니다.")
        String template
) {
}
