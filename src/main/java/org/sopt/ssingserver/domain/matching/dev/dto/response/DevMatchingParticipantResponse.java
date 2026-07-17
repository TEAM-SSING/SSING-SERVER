package org.sopt.ssingserver.domain.matching.dev.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.sopt.ssingserver.domain.member.enums.Gender;

@Schema(description = "매칭 요청에 포함된 강습 참가자 원본 row")
public record DevMatchingParticipantResponse(
        @Schema(description = "참가자 ID", example = "501")
        Long participantId,
        @Schema(description = "참가자가 속한 매칭 요청 ID", example = "301")
        Long matchingRequestId,
        @Schema(
                description = "참가자 이름. V6 적용 전 기존 row는 null일 수 있음",
                example = "김참가자",
                maxLength = 50,
                nullable = true,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String name,
        @Schema(description = "참가자 나이", example = "24")
        int age,
        @Schema(description = "참가자 성별", example = "MALE")
        Gender gender
) {
}
