package org.sopt.ssingserver.domain.lesson.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.lesson.dto.response.LessonCompletionResponse;
import org.sopt.ssingserver.domain.lesson.entity.Lesson;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;
import org.sopt.ssingserver.domain.lesson.error.LessonErrorCode;
import org.sopt.ssingserver.domain.lesson.realtime.LessonRealtimeEventFactory;
import org.sopt.ssingserver.domain.lesson.realtime.LessonRealtimeEventFactory.CompletedRealtimeContext;
import org.sopt.ssingserver.domain.lesson.realtime.LessonRealtimeEventFactory.ConsumerRecipient;
import org.sopt.ssingserver.domain.lesson.realtime.LessonRealtimeEventPublisher;
import org.sopt.ssingserver.domain.lesson.repository.LessonParticipantRepository;
import org.sopt.ssingserver.domain.lesson.repository.LessonRepository;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.sopt.ssingserver.global.time.AppZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LessonCompletionService {

    private final LessonRepository lessonRepository;
    private final LessonParticipantRepository lessonParticipantRepository;
    private final LessonRealtimeEventFactory lessonRealtimeEventFactory;
    private final LessonRealtimeEventPublisher lessonRealtimeEventPublisher;
    private final LessonAfterCommitExecutor lessonAfterCommitExecutor;
    private final Clock clock;

    @Transactional
    public LessonCompletionResponse complete(
            CurrentMember currentMember,
            Long lessonId
    ) {
        // 강습 상태 변경 중복 처리를 막기 위해 lesson row를 잠금 조회
        Lesson lesson = lessonRepository.findByIdForUpdate(lessonId)
                .orElseThrow(() -> new BusinessException(LessonErrorCode.LESSON_NOT_FOUND));

        // WebSocket 수신자와 대표 소비자 권한 판별에 사용할 강습 참여 팀을 조회
        List<MatchingRequest> matchingRequests =
                lessonParticipantRepository.findDistinctMatchingRequestsByLessonId(lessonId);
        List<ConsumerRecipient> consumerRecipients = matchingRequests.stream()
                .map(matchingRequest -> new ConsumerRecipient(
                        matchingRequest.getMember().getId(),
                        matchingRequest.getId(),
                        matchingRequest.getMember(),
                        matchingRequest
                ))
                .toList();
        Long instructorMemberId = lesson.getInstructorProfile().getMember().getId();

        // 현재 요청자가 담당 강사 또는 대표 소비자인지 확인
        boolean instructorRequester = currentMember.isApprovedInstructor()
                && Objects.equals(instructorMemberId, currentMember.memberId());
        boolean consumerRequester = currentMember.isActiveConsumer()
                && consumerRecipients.stream()
                .anyMatch(recipient -> Objects.equals(recipient.memberId(), currentMember.memberId()));
        if (!instructorRequester && !consumerRequester) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        // 진행 중인 강습만 종료할 수 있음
        if (lesson.getStatus() != LessonStatus.IN_PROGRESS) {
            throw new BusinessException(LessonErrorCode.LESSON_COMPLETE_NOT_ALLOWED);
        }

        Instant now = clock.instant();
        lesson.complete(now);

        // DB 커밋 이후 강습 참여 대상에게 종료 이벤트 전송
        lessonAfterCommitExecutor.execute(
                "lesson-realtime-event-publish",
                () -> lessonRealtimeEventPublisher.publish(lessonRealtimeEventFactory.completed(
                        new CompletedRealtimeContext(
                                UUID.randomUUID(),
                                now,
                                lesson.getId(),
                                instructorMemberId,
                                consumerRecipients
                        )
                ))
        );

        return LessonCompletionResponse.completed(
                lesson.getId(),
                now.atZone(AppZoneId.SEOUL).toOffsetDateTime()
        );
    }
}
