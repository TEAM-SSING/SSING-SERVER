package org.sopt.ssingserver.domain.lesson.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;

public record LessonCompletionResponse(
        @Schema(description = "강습 ID", example = "1")
        Long lessonId,

        @Schema(description = "강습 상태", example = "COMPLETED")
        LessonStatus lessonStatus,

        @Schema(description = "강습 종료 시각", example = "2026-06-28T17:31:00+09:00")
        OffsetDateTime completedAt
) {

    public static LessonCompletionResponse completed(
            Long lessonId,
            OffsetDateTime completedAt
    ) {
        return new LessonCompletionResponse(
                lessonId,
                LessonStatus.COMPLETED,
                completedAt
        );
    }
}
