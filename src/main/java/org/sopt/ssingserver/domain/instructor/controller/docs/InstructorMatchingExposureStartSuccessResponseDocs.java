package org.sopt.ssingserver.domain.instructor.controller.docs;

import io.swagger.v3.oas.annotations.media.Schema;
import org.sopt.ssingserver.domain.instructor.dto.response.InstructorMatchingExposureResponse;

@Schema(
        name = "InstructorMatchingExposureStartSuccessResponse",
        description = "강사 즉시 매칭 조건 저장 및 시작 성공 응답"
)
public record InstructorMatchingExposureStartSuccessResponseDocs(
        @Schema(
                description = "요청 성공 여부",
                example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        boolean success,

        @Schema(
                description = "성공 응답 코드",
                example = "INSTRUCTOR_MATCHING_EXPOSURE_STARTED",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String code,

        @Schema(
                description = "성공 메시지",
                example = "즉시 매칭 노출이 시작되었습니다.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String message,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        InstructorMatchingExposureResponse data
) {
}
