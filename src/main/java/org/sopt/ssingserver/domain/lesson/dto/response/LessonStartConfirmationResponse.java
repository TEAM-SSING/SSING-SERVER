package org.sopt.ssingserver.domain.lesson.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LessonStartConfirmationResponse(
        @Schema(description = "강습 ID", example = "30")
        Long lessonId,

        @Schema(description = "강습 상태", example = "CONFIRMED")
        LessonStatus lessonStatus,

        @Schema(description = "시작 확인 현황. 아직 다른 확인 대상이 남아 있을 때 사용")
        StatusInfoResponse statusInfo,

        @Schema(description = "실제 강습 시작 시각", example = "2026-06-28T15:31:00+09:00")
        OffsetDateTime startedAt
) {

    public static LessonStartConfirmationResponse pending(
            Long lessonId,
            StatusInfoResponse statusInfo
    ) {
        return new LessonStartConfirmationResponse(
                lessonId,
                LessonStatus.CONFIRMED,
                statusInfo,
                null
        );
    }

    public static LessonStartConfirmationResponse started(
            Long lessonId,
            OffsetDateTime startedAt
    ) {
        return new LessonStartConfirmationResponse(
                lessonId,
                LessonStatus.IN_PROGRESS,
                null,
                startedAt
        );
    }

    public boolean started() {
        return lessonStatus == LessonStatus.IN_PROGRESS;
    }

    public record StatusInfoResponse(
            @Schema(description = "강습 시작을 누른 강사와 팀 수", example = "2")
            int confirmedCount,

            @Schema(description = "강습 시작을 눌러야 하는 강사와 팀 수", example = "3")
            int requiredCount,

            @Schema(description = "현재 호출자의 시작 확인 완료 여부", example = "true")
            boolean currentActorConfirmed,

            @Schema(description = "강사의 시작 확인 완료 여부", example = "true")
            boolean instructorConfirmed
    ) {
    }
}
