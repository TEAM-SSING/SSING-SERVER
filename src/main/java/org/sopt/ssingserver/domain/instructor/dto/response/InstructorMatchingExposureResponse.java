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
                description = "선택 시간 범위와 최대 인원의 중간값으로 계산해 500원 단위로 반올림한 예상 강습비",
                example = "87500",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        int estimatedLessonPriceAmount,

        @Schema(
                description = "매칭 대기 화면에 표시할 강사의 120분 기준 저장 가격",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        InstructorPricePolicyResponse pricePolicy
) {

    public static InstructorMatchingExposureResponse of(
            boolean isExposed,
            int estimatedLessonPriceAmount,
            int basePriceAmount,
            int additionalPersonPriceAmount
    ) {
        return new InstructorMatchingExposureResponse(
                isExposed,
                estimatedLessonPriceAmount,
                InstructorPricePolicyResponse.of(basePriceAmount, additionalPersonPriceAmount)
        );
    }
}
