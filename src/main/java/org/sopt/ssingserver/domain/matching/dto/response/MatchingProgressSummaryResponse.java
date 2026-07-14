package org.sopt.ssingserver.domain.matching.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingProgressSummaryResult;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "최종 확인 또는 결제 단계의 서버 기준 절대 진행률")
public record MatchingProgressSummaryResponse(
        @Schema(description = "최종 확인을 완료한 대표 소비자 요청 수. 최종 확인 단계에서만 포함", example = "1")
        Integer acceptedRequesterCount,

        @Schema(description = "현재 그룹 또는 제안에 포함된 전체 대표 소비자 요청 수", example = "2")
        int totalRequesterCount,

        @Schema(description = "결제를 완료한 대표 소비자 요청 수. 결제 단계에서만 포함", example = "1")
        Integer paidRequesterCount
) {

    public static MatchingProgressSummaryResponse from(MatchingProgressSummaryResult result) {
        if (result == null) {
            return null;
        }
        return new MatchingProgressSummaryResponse(
                result.acceptedRequesterCount(),
                result.totalRequesterCount(),
                result.paidRequesterCount()
        );
    }
}
