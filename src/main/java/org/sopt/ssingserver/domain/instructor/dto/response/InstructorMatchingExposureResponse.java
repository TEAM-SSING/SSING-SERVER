package org.sopt.ssingserver.domain.instructor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record InstructorMatchingExposureResponse(
        @Schema(
                description = "즉시노출 여부",
                example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        boolean isExposed,

        @Schema(
                description = "매칭 대기 화면에 표시할 강사의 120분 기준 저장 가격",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        InstructorPricePolicyResponse pricePolicy
) {

    public static InstructorMatchingExposureResponse of(
            boolean isExposed,
            int basePriceAmount,
            int additionalPersonPriceAmount
    ) {
        return new InstructorMatchingExposureResponse(
                isExposed,
                InstructorPricePolicyResponse.of(basePriceAmount, additionalPersonPriceAmount)
        );
    }
}
