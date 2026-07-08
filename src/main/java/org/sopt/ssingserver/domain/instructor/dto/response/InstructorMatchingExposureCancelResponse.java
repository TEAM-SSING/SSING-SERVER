package org.sopt.ssingserver.domain.instructor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record InstructorMatchingExposureCancelResponse(
        @Schema(description = "즉시노출 여부", example = "false")
        boolean isExposed,

        @Schema(description = "즉시노출 설정 마지막 수정 시각", example = "2026-06-28T15:31:00+09:00")
        OffsetDateTime updatedAt
) {

    private static final ZoneOffset KOREA_TIME_OFFSET = ZoneOffset.ofHours(9);

    public static InstructorMatchingExposureCancelResponse of(boolean isExposed, Instant updatedAt) {
        return new InstructorMatchingExposureCancelResponse(isExposed, updatedAt.atOffset(KOREA_TIME_OFFSET));
    }
}
