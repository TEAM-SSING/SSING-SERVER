package org.sopt.ssingserver.domain.instructor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "InstructorPricePolicy", description = "강사가 저장한 120분 기준 가격 정책")
public record InstructorPricePolicyResponse(
        @Schema(description = "가격 기준 강습 시간(분)", example = "120", requiredMode = Schema.RequiredMode.REQUIRED)
        int baseDurationMinutes,

        @Schema(description = "120분 기준 1인 기본 가격", example = "60000", requiredMode = Schema.RequiredMode.REQUIRED)
        int basePriceAmount,

        @Schema(description = "120분 기준 1인 추가 가격", example = "20000", requiredMode = Schema.RequiredMode.REQUIRED)
        int additionalPersonPriceAmount
) {

    private static final int BASE_DURATION_MINUTES = 120;

    public static InstructorPricePolicyResponse of(
            int basePriceAmount,
            int additionalPersonPriceAmount
    ) {
        return new InstructorPricePolicyResponse(
                BASE_DURATION_MINUTES,
                basePriceAmount,
                additionalPersonPriceAmount
        );
    }
}
