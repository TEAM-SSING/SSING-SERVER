package org.sopt.ssingserver.domain.matching.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.sopt.ssingserver.domain.matching.dto.result.InstructorPriceSummaryResult;

@Schema(name = "InstructorMatchingPriceSummary", description = "리조트 패찰비를 제외한 강사 자기 강습 정산 금액")
public record InstructorPriceSummaryResponse(
        @Schema(
                description = "제안 생성 시점에 고정된 강사 정산 금액. MVP 플랫폼 수수료는 0원",
                example = "80000",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        int instructorSettlementAmount
) {

    public static InstructorPriceSummaryResponse from(InstructorPriceSummaryResult result) {
        return new InstructorPriceSummaryResponse(result.instructorSettlementAmount());
    }
}
