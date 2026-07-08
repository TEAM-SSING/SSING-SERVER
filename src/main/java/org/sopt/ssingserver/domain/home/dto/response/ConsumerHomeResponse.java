package org.sopt.ssingserver.domain.home.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;
import org.sopt.ssingserver.domain.lesson.repository.projection.HomeLessonCardProjection;
import org.sopt.ssingserver.global.time.AppZoneId;

public record ConsumerHomeResponse(
        @Schema(description = "홈에 표시할 예약/진행 중 강습 카드 목록")
        List<LessonCardResponse> lessonCards,

        @Schema(description = "읽지 않은 알림 존재 여부", example = "true")
        boolean hasUnreadNotification
) {

    public static ConsumerHomeResponse from(
            List<LessonCardResponse> lessonCards,
            boolean hasUnreadNotification
    ) {
        return new ConsumerHomeResponse(lessonCards, hasUnreadNotification);
    }

    public record LessonCardResponse(
            @Schema(description = "강습 ID", example = "1")
            Long lessonId,

            @Schema(description = "강습 예정일까지 남은 날짜", example = "2")
            int remainingDays,

            @Schema(description = "강습 상태", example = "CONFIRMED")
            LessonStatus displayStatus,

            @Schema(description = "홈 강습 카드 제목", example = "김철수님 팀 4명")
            String title,

            @Schema(description = "강습 예정 일시", example = "2025-07-15T19:00:00+09:00")
            OffsetDateTime scheduledAt,

            @Schema(description = "리조트 정보")
            ResortResponse resort
    ) {

        public static LessonCardResponse from(
                HomeLessonCardProjection lessonCard,
                int remainingDays,
                String title
        ) {
            return new LessonCardResponse(
                    lessonCard.getLessonId(),
                    remainingDays,
                    lessonCard.getLessonStatus(),
                    title,
                    lessonCard.getScheduledAt().atZone(AppZoneId.SEOUL).toOffsetDateTime(),
                    new ResortResponse(lessonCard.getResortCode(), lessonCard.getResortDisplayName())
            );
        }
    }

    public record ResortResponse(
            @Schema(description = "리조트 코드", example = "HIGH1")
            String code,

            @Schema(description = "Android 표시용 리조트 이름", example = "하이원")
            String displayName
    ) {
    }
}
