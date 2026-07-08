package org.sopt.ssingserver.domain.home.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.home.dto.response.ConsumerHomeResponse;
import org.sopt.ssingserver.domain.home.dto.response.ConsumerHomeResponse.LessonCardResponse;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;
import org.sopt.ssingserver.domain.lesson.repository.LessonParticipantRepository;
import org.sopt.ssingserver.domain.lesson.repository.projection.HomeLessonCardProjection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HomeService {

    private static final List<LessonStatus> UPCOMING_LESSON_STATUSES = List.of(
            LessonStatus.CONFIRMED,
            LessonStatus.IN_PROGRESS
    );
    private static final ZoneId RESOLVE_ZONE = ZoneId.of("Asia/Seoul");

    private final LessonParticipantRepository lessonParticipantRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ConsumerHomeResponse getHome(Long memberId) {
        List<HomeLessonCardProjection> lessonCards = lessonParticipantRepository
                .findHomeLessonCardsByMemberIdAndLessonStatusIn(memberId, UPCOMING_LESSON_STATUSES);

        Instant now = clock.instant();
        List<LessonCardResponse> lessonCardResponses = lessonCards.stream()
                .map(lessonCard -> LessonCardResponse.from(
                        lessonCard,
                        resolveRemainingDays(lessonCard.getLessonStatus(), lessonCard.getScheduledAt(), now),
                        resolveTitle(lessonCard)
                ))
                .toList();

        // TODO: 알림 읽음 여부 정책이 확정되면 실제 조회로 교체
        boolean hasUnreadNotification = false;

        return ConsumerHomeResponse.from(lessonCardResponses, hasUnreadNotification);
    }

    private int resolveRemainingDays(LessonStatus lessonStatus, Instant scheduledAt, Instant now) {
        if (lessonStatus == LessonStatus.IN_PROGRESS) {
            return 0;
        }

        LocalDate today = now.atZone(RESOLVE_ZONE).toLocalDate();
        LocalDate scheduledDate = scheduledAt.atZone(RESOLVE_ZONE).toLocalDate();
        return (int) Math.max(0, ChronoUnit.DAYS.between(today, scheduledDate));
    }

    private String resolveTitle(HomeLessonCardProjection lessonCard) {
        return lessonCard.getRequesterNickname() + "님 팀 " + lessonCard.getTotalHeadcount() + "명";
    }
}
