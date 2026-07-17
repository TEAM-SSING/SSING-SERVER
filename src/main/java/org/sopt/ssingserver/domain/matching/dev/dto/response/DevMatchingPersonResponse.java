package org.sopt.ssingserver.domain.matching.dev.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.sopt.ssingserver.domain.matching.dev.enums.DevMatchingPersonRole;

@Schema(description = "개발용 매칭 관계에 연결된 사람")
public record DevMatchingPersonResponse(
        @Schema(description = "매칭에서의 역할", example = "CONSUMER")
        DevMatchingPersonRole personRole,
        @Schema(description = "회원 ID", example = "12")
        Long memberId,
        @Schema(description = "강사 프로필 ID. 강습생이면 null", example = "5")
        Long instructorProfileId,
        @Schema(description = "개발용 persona key. 일반 회원이면 null", example = "consumer-matching-a")
        String personaKey,
        @Schema(description = "화면 표시 이름", example = "매칭 강습생 A")
        String displayName
) {
}
