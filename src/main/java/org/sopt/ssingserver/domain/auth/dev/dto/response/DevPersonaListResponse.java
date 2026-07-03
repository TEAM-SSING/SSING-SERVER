package org.sopt.ssingserver.domain.auth.dev.dto.response;

import java.util.List;

public record DevPersonaListResponse(
        List<DevPersonaResponse> personas
) {
}
