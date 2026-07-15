package org.sopt.ssingserver.domain.home.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.global.time.AppZoneId;

public record InstructorHomeResponse(
        @Schema(description = "홈 상단 매칭/강습 카드 목록")
        List<LessonCardResponse> lessonCards,

        @Schema(description = "전체 서비스에서 매칭 중인 사람 수", example = "99")
        long matchingPeopleCount,

        @Schema(description = "로그인한 강사의 본명", example = "김씽씽")
        String instructorName,

        @Schema(description = "강습 후기/평점/Grade 요약")
        ReviewSummaryResponse reviewSummary,

        @Schema(description = "읽지 않은 알림 존재 여부", example = "false")
        boolean hasUnreadNotification
) {

    public static InstructorHomeResponse from(
            List<LessonCardResponse> lessonCards,
            long matchingPeopleCount,
            String instructorName,
            ReviewSummaryResponse reviewSummary,
            boolean hasUnreadNotification
    ) {
        return new InstructorHomeResponse(
                lessonCards,
                matchingPeopleCount,
                instructorName,
                reviewSummary,
                hasUnreadNotification
        );
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReviewSummaryResponse(
            @Schema(description = "평균 평점", example = "3.0")
            Double averageRating,

            @Schema(description = "강사 등급", example = "4")
            Integer grade,

            @Schema(description = "등급 달성률", example = "88")
            Integer achievementRate
    ) {

        public static ReviewSummaryResponse empty() {
            return new ReviewSummaryResponse(null, null, null);
        }

        public static ReviewSummaryResponse from(
                double averageRating,
                int grade,
                int achievementRate
        ) {
            return new ReviewSummaryResponse(averageRating, grade, achievementRate);
        }
    }

    @Schema(name = "InstructorHomeLessonCardResponse")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LessonCardResponse(
            @Schema(
                    description = "동일 매칭 흐름을 식별하는 제안 ID. 제안 단계와 CONFIRMED/IN_PROGRESS 강습 카드에서 제공",
                    example = "21"
            )
            Long offerId,

            @Schema(description = "강습 상세 조회에 사용하는 ID. CONFIRMED/IN_PROGRESS 카드에서 제공", example = "2")
            Long lessonId,

            @Schema(description = "강습 예정일까지 남은 날짜", example = "7")
            int remainingDays,

            @Schema(description = "매칭/강습 카드 상태", example = "MATCHING")
            String displayStatus,

            @Schema(description = "카드 제목", example = "김OO님 팀 3명")
            String title,

            @Schema(description = "강습 종목", example = "SKI")
            Sport sport,

            @Schema(description = "강습 예정 일시", example = "2025-07-20T10:00:00+09:00")
            OffsetDateTime scheduledAt,

            @Schema(description = "리조트 정보")
            ResortResponse resort
    ) {

        public static LessonCardResponse from(
                Long offerId,
                Long lessonId,
                int remainingDays,
                String displayStatus,
                String title,
                Sport sport,
                Instant scheduledAt,
                String resortCode,
                String resortDisplayName
        ) {
            return new LessonCardResponse(
                    offerId,
                    lessonId,
                    remainingDays,
                    displayStatus,
                    title,
                    sport,
                    toOffsetDateTime(scheduledAt),
                    new ResortResponse(resortCode, resortDisplayName)
            );
        }

        private static OffsetDateTime toOffsetDateTime(Instant instant) {
            return instant.atZone(AppZoneId.SEOUL).toOffsetDateTime();
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
