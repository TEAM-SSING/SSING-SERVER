package org.sopt.ssingserver.domain.auth.dev.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "개발용 persona 목록")
public record DevPersonaListResponse(
        @Schema(description = "생성 순으로 정렬된 합성 persona 목록")
        List<DevPersonaResponse> personas
) {

    public static DevPersonaListResponse from(List<DevPersonaResponse> personas) {
        return new DevPersonaListResponse(personas);
    }
}
