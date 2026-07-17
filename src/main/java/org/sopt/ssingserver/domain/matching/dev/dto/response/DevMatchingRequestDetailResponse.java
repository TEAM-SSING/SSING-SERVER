package org.sopt.ssingserver.domain.matching.dev.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.sopt.ssingserver.domain.matching.dev.enums.DevMatchingResolutionState;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;

@Schema(description = "개발용 매칭 요청 관계 상세와 동작 영향 미리보기")
public record DevMatchingRequestDetailResponse(
        @Schema(description = "DB 조회 완료 시각", example = "2026-07-15T00:01:00Z")
        Instant observedAt,
        @Schema(description = "조회 snapshot fingerprint. 후속 실행 API의 변경 감지에 사용")
        String stateToken,
        @Schema(description = "선택한 매칭 요청 ID", example = "301")
        Long matchingRequestId,
        @Schema(description = "관계 해석 결과", example = "RESOLVED")
        DevMatchingResolutionState resolutionState,
        @Schema(description = "선택 요청의 계산된 앱 매칭 상태", example = "PAYMENT_PENDING")
        MatchingStatus matchingStatus,
        @Schema(description = "선택 요청의 원본 상태", example = "MATCHED")
        MatchingRequestStatus requestStatus,
        @Schema(description = "선택 요청의 원본 상태 사유")
        MatchingRequestStatusReason requestStatusReason,
        @Schema(description = "현재 관계에 포함된 강습생과 강사")
        List<DevMatchingPersonResponse> people,
        @Schema(description = "강습생별 request, item, payment 연결표")
        List<DevMatchingRequestRelationResponse> requestRelations,
        @Schema(description = "관련 요청의 강습 참가자 원본 row")
        List<DevMatchingParticipantResponse> participants,
        @Schema(description = "상태 판단에 사용한 원본 리소스 row")
        List<DevMatchingResourceResponse> resources,
        @Schema(description = "현재 상태의 동작 영향과 dev 실행 허용 여부")
        List<DevMatchingActionPreviewResponse> availableActions,
        @Schema(description = "깨진 관계나 상태 조합을 설명하는 진단 메시지")
        List<String> diagnostics,
        @Schema(description = "실행을 제한한 범위와 상태 변경 충돌 안내")
        List<String> actionLimitations
) {
}
