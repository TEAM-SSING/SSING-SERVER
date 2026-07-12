package org.sopt.ssingserver.domain.auth.dev.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "개발용 persona 생성 결과")
public record CreateDevPersonaResponse(
        @Schema(description = "생성된 persona")
        DevPersonaResponse persona
) {

    public static CreateDevPersonaResponse from(DevPersonaResponse persona) {
        return new CreateDevPersonaResponse(persona);
    }
}
