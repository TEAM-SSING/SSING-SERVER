package org.sopt.ssingserver.domain.matching.dev.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.sopt.ssingserver.domain.matching.dev.enums.DevMatchingActionKey;
import org.sopt.ssingserver.domain.matching.dev.enums.DevMatchingResolutionState;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;

@Schema(description = "개발용 매칭 요청 목록의 한 행")
public record DevMatchingRequestSummaryResponse(
        @Schema(description = "매칭 요청 ID", example = "301")
        Long matchingRequestId,
        @Schema(description = "요청한 강습생 회원 ID", example = "12")
        Long consumerMemberId,
        @Schema(description = "강습생 persona key", example = "consumer-matching-a")
        String consumerPersonaKey,
        @Schema(description = "강습생 표시 이름", example = "매칭 강습생 A")
        String consumerName,
        @Schema(description = "관계 해석 결과", example = "RESOLVED")
        DevMatchingResolutionState resolutionState,
        @Schema(description = "계산된 앱 매칭 상태", example = "WAITING_FOR_INSTRUCTOR")
        MatchingStatus matchingStatus,
        @Schema(description = "원본 매칭 요청 상태", example = "GROUPED")
        MatchingRequestStatus requestStatus,
        @Schema(description = "원본 매칭 요청 상태 사유")
        MatchingRequestStatusReason requestStatusReason,
        @Schema(description = "현재 그룹 ID", example = "98")
        Long groupId,
        @Schema(description = "현재 강사 제안 ID", example = "77")
        Long offerId,
        @Schema(description = "현재 원본 상태에서 가능한 미리보기 동작 key")
        List<DevMatchingActionKey> availableActionKeys,
        @Schema(description = "깨진 관계나 상태 조합을 설명하는 진단 메시지")
        List<String> diagnostics,
        @Schema(description = "매칭 요청 최종 수정 시각", example = "2026-07-15T00:01:00Z")
        Instant updatedAt
) {
}
