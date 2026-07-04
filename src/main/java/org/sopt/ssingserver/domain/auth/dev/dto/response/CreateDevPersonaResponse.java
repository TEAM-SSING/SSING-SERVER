package org.sopt.ssingserver.domain.auth.dev.dto.response;

public record CreateDevPersonaResponse(
        DevPersonaResponse persona
) {

    public static CreateDevPersonaResponse from(DevPersonaResponse persona) {
        return new CreateDevPersonaResponse(persona);
    }
}
