package org.sopt.ssingserver.domain.auth.dev.dto.response;

import org.sopt.ssingserver.domain.auth.dev.enums.DevPersonaTemplate;

public record DevMetaResponse(
        String personaOrigin,
        String accountState
) {

    private static final String MANUAL_ORIGIN = "MANUAL";

    public static DevMetaResponse manual(DevPersonaTemplate template) {
        return new DevMetaResponse(MANUAL_ORIGIN, template.name());
    }
}
