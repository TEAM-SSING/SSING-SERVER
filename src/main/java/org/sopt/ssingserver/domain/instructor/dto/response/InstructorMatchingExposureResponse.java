package org.sopt.ssingserver.domain.instructor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record InstructorMatchingExposureResponse(
        @Schema(description = "즉시노출 여부", example = "true")
        boolean isExposed
) {

    public static InstructorMatchingExposureResponse from(boolean isExposed) {
        return new InstructorMatchingExposureResponse(isExposed);
    }
}
