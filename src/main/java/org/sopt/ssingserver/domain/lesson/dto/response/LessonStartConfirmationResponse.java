package org.sopt.ssingserver.domain.lesson.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
        name = "LessonStartConfirmationResponse",
        description = "강습 시작 확인 응답. lessonStatus에 따라 응답 구조가 달라집니다.",
        discriminatorProperty = "lessonStatus",
        oneOf = {
                LessonStartConfirmationResponse.Pending.class,
                LessonStartConfirmationResponse.Started.class
        }
)
public sealed interface LessonStartConfirmationResponse permits
        LessonStartConfirmationResponse.Pending,
        LessonStartConfirmationResponse.Started {

    @Schema(description = "강습 ID", example = "30")
    Long lessonId();

    @Schema(description = "강습 상태", example = "CONFIRMED")
    LessonStatus lessonStatus();

    default StatusInfoResponse statusInfo() {
        return null;
    }

    default OffsetDateTime startedAt() {
        return null;
    }

    default boolean started() {
        return lessonStatus() == LessonStatus.IN_PROGRESS;
    }

    static LessonStartConfirmationResponse pending(
            Long lessonId,
            StatusInfoResponse statusInfo
    ) {
        return new Pending(lessonId, LessonStatus.CONFIRMED, statusInfo);
    }

    static LessonStartConfirmationResponse started(
            Long lessonId,
            OffsetDateTime startedAt
    ) {
        return new Started(lessonId, LessonStatus.IN_PROGRESS, startedAt);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "LessonStartConfirmationPending", description = "강습 시작 확인 - 다른 확인 대상 대기")
    record Pending(
            @Schema(description = "강습 ID", example = "30")
            Long lessonId,

            @Schema(description = "강습 상태", example = "CONFIRMED")
            LessonStatus lessonStatus,

            @Schema(description = "시작 확인 현황")
            StatusInfoResponse statusInfo
    ) implements LessonStartConfirmationResponse {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "LessonStartConfirmationStarted", description = "강습 시작 확인 - 강습 시작")
    record Started(
            @Schema(description = "강습 ID", example = "30")
            Long lessonId,

            @Schema(description = "강습 상태", example = "IN_PROGRESS")
            LessonStatus lessonStatus,

            @Schema(description = "실제 강습 시작 시각", example = "2026-06-28T15:31:00+09:00")
            OffsetDateTime startedAt
    ) implements LessonStartConfirmationResponse {
    }

    record StatusInfoResponse(
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
