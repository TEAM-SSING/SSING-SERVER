package org.sopt.ssingserver.domain.home.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.home.dto.response.ConsumerHomeResponse;
import org.sopt.ssingserver.domain.home.dto.response.ConsumerHomeResponse.LessonCardResponse;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;
import org.sopt.ssingserver.domain.lesson.repository.LessonParticipantRepository;
import org.sopt.ssingserver.domain.lesson.repository.projection.HomeLessonCardProjection;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestRepository;
import org.sopt.ssingserver.global.time.AppZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConsumerHomeService {

    private static final List<LessonStatus> UPCOMING_LESSON_STATUSES = List.of(
            LessonStatus.CONFIRMED,
            LessonStatus.IN_PROGRESS
    );
    private static final List<MatchingRequestStatus> MATCHING_CONSUMER_COUNT_STATUSES = List.of(
            MatchingRequestStatus.REQUESTED,
            MatchingRequestStatus.GROUPED,
            MatchingRequestStatus.MATCHED
    );

    private final LessonParticipantRepository lessonParticipantRepository;
    private final MatchingRequestRepository matchingRequestRepository;
    private final Clock clock;

    // 소비자 홈에 표시할 예약/진행 강습 카드 조회함
    @Transactional(readOnly = true)
    public ConsumerHomeResponse getConsumerHome(Long memberId) {
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

        // TODO: 알림 읽음 여부 정책 확정 후 실제 조회로 교체함
        boolean hasUnreadNotification = false;
        long matchingConsumerCount = matchingRequestRepository.countByStatusIn(MATCHING_CONSUMER_COUNT_STATUSES);

        return ConsumerHomeResponse.from(
                lessonCardResponses,
                matchingConsumerCount,
                hasUnreadNotification
        );
    }

    // 강습 상태와 예정일 기준으로 홈 카드의 D-day 값 계산함
    private int resolveRemainingDays(LessonStatus lessonStatus, Instant scheduledAt, Instant now) {
        if (lessonStatus == LessonStatus.IN_PROGRESS) {
            return 0;
        }

        LocalDate today = now.atZone(AppZoneId.SEOUL).toLocalDate();
        LocalDate scheduledDate = scheduledAt.atZone(AppZoneId.SEOUL).toLocalDate();
        return (int) Math.max(0, ChronoUnit.DAYS.between(today, scheduledDate));
    }

    // 소비자 홈 카드 제목을 대표 소비자 닉네임과 전체 인원으로 생성함
    private String resolveTitle(HomeLessonCardProjection lessonCard) {
        return lessonCard.getRequesterNickname() + "님 팀 " + lessonCard.getTotalHeadcount() + "명";
    }
}
