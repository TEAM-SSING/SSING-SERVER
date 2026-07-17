package org.sopt.ssingserver.domain.matching.dev.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;

@Schema(description = "한 강습생 요청과 group item, payment를 연결한 관계 행")
public record DevMatchingRequestRelationResponse(
        @Schema(description = "매칭 요청 ID", example = "301")
        Long matchingRequestId,
        @Schema(description = "요청한 강습생 회원 ID", example = "12")
        Long consumerMemberId,
        @Schema(description = "현재 매칭 요청 그룹 item ID", example = "302")
        Long groupItemId,
        @Schema(description = "현재 매칭 요청 그룹 ID", example = "98")
        Long groupId,
        @Schema(description = "현재 강사 제안 ID", example = "77")
        Long offerId,
        @Schema(description = "이 요청의 결제 ID. 결제 전이면 null", example = "401")
        Long paymentId,
        @Schema(description = "이 요청에 대해 계산한 앱 매칭 상태", example = "PAYMENT_PENDING")
        MatchingStatus matchingStatus
) {
}
