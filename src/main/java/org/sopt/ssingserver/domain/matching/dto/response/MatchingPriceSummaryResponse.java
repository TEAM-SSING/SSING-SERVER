package org.sopt.ssingserver.domain.matching.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingPriceSummaryResult;

@Schema(description = "강사 제안 시점에 고정된 요청별 가격 요약")
public record MatchingPriceSummaryResponse(
        @Schema(description = "요청별 강습비", example = "60000")
        int lessonPriceAmount,

        @Schema(description = "리조트 패찰비", example = "20000")
        int resortPassFeeAmount,

        @Schema(description = "강습비와 패찰비를 합한 최종 결제금액", example = "80000")
        int totalPaymentAmount
) {

    public static MatchingPriceSummaryResponse from(MatchingPriceSummaryResult result) {
        if (result == null) {
            return null;
        }

        return new MatchingPriceSummaryResponse(
                result.lessonPriceAmount(),
                result.resortPassFeeAmount(),
                result.totalPaymentAmount()
        );
    }
}
