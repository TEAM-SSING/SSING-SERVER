package org.sopt.ssingserver.domain.instructor.controller.docs;

import io.swagger.v3.oas.annotations.media.Schema;
import org.sopt.ssingserver.domain.instructor.dto.response.InstructorMatchingExposureConditionsResponse;

@Schema(
        name = "InstructorMatchingExposureConditionsSuccessResponse",
        description = "강사 즉시노출 조건 화면 조회 성공 응답"
)
public record InstructorMatchingExposureConditionsSuccessResponseDocs(
        @Schema(
                description = "요청 성공 여부",
                example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        boolean success,

        @Schema(
                description = "성공 응답 코드",
                example = "SUCCESS",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String code,

        @Schema(
                description = "성공 메시지",
                example = "요청이 성공했습니다.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String message,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        InstructorMatchingExposureConditionsResponse data
) {
}
