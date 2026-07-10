package org.sopt.ssingserver.domain.lesson.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.lesson.dto.request.LessonCancellationRequest;
import org.sopt.ssingserver.domain.lesson.dto.response.LessonCancellationResponse;
import org.sopt.ssingserver.domain.lesson.entity.Lesson;
import org.sopt.ssingserver.domain.lesson.entity.LessonCancellation;
import org.sopt.ssingserver.domain.lesson.enums.LessonCancellationActor;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;
import org.sopt.ssingserver.domain.lesson.error.LessonErrorCode;
import org.sopt.ssingserver.domain.lesson.realtime.LessonRealtimeEventFactory;
import org.sopt.ssingserver.domain.lesson.realtime.LessonRealtimeEventFactory.CanceledRealtimeContext;
import org.sopt.ssingserver.domain.lesson.realtime.LessonRealtimeEventFactory.ConsumerRecipient;
import org.sopt.ssingserver.domain.lesson.realtime.LessonRealtimeEventPublisher;
import org.sopt.ssingserver.domain.lesson.repository.LessonCancellationRepository;
import org.sopt.ssingserver.domain.lesson.repository.LessonParticipantRepository;
import org.sopt.ssingserver.domain.lesson.repository.LessonRepository;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.sopt.ssingserver.global.time.AppZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LessonCancellationService {

    private final LessonRepository lessonRepository;
    private final LessonParticipantRepository lessonParticipantRepository;
    private final LessonCancellationRepository lessonCancellationRepository;
    private final LessonRealtimeEventFactory lessonRealtimeEventFactory;
    private final LessonRealtimeEventPublisher lessonRealtimeEventPublisher;
    private final LessonAfterCommitExecutor lessonAfterCommitExecutor;
    private final Clock clock;

    @Transactional
    public LessonCancellationResponse cancel(
            CurrentMember currentMember,
            Long lessonId,
            LessonCancellationRequest request
    ) {
        // 강습 상태 변경 중복 처리를 막기 위해 lesson row를 잠금 조회
        Lesson lesson = lessonRepository.findByIdForUpdate(lessonId)
                .orElseThrow(() -> new BusinessException(LessonErrorCode.LESSON_NOT_FOUND));

        // 강습 참여 팀 조회
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

        // 현재 요청자가 담당 강사 또는 소비자인지 확인
        MatchingRequest requesterMatchingRequest = matchingRequests.stream()
                .filter(matchingRequest -> matchingRequest.getMember().getId().equals(currentMember.memberId()))
                .findFirst()
                .orElse(null);
        boolean instructorRequester = currentMember.isApprovedInstructor()
                && instructorMemberId.equals(currentMember.memberId());
        boolean consumerRequester = currentMember.isActiveConsumer() && requesterMatchingRequest != null;
        if (!instructorRequester && !consumerRequester) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        // 이미 같은 회원이 취소한 이력이 있으면 중복 취소를 거절
        if (lessonCancellationRepository.existsByLessonIdAndMemberId(lessonId, currentMember.memberId())) {
            throw new BusinessException(LessonErrorCode.LESSON_CANCEL_NOT_ALLOWED);
        }

        // 확정된 강습만 취소할 수 있음
        if (lesson.getStatus() != LessonStatus.CONFIRMED) {
            throw new BusinessException(LessonErrorCode.LESSON_CANCEL_NOT_ALLOWED);
        }

        Instant now = clock.instant();
        lesson.cancel(now);
        // TODO: 다중 매칭 정책 확정 시 소비자 팀 일부 취소에 대해 처리한다.
        matchingRequests.forEach(MatchingRequest::cancelByLessonCancellation);

        // 취소 주체와 표시용 취소 사유를 저장
        LessonCancellationActor canceledBy = instructorRequester
                ? LessonCancellationActor.INSTRUCTOR
                : LessonCancellationActor.CONSUMER;
        Member cancelMember = instructorRequester
                ? lesson.getInstructorProfile().getMember()
                : requesterMatchingRequest.getMember();
        MatchingRequest canceledMatchingRequest = instructorRequester ? null : requesterMatchingRequest;
        lessonCancellationRepository.save(LessonCancellation.create(
                lesson,
                canceledMatchingRequest,
                cancelMember,
                canceledBy,
                resolveCancelReason(request),
                now
        ));

        // DB 커밋 이후 강습 참여 대상에게 취소 이벤트 전송
        lessonAfterCommitExecutor.execute(
                "lesson-realtime-event-publish",
                () -> lessonRealtimeEventPublisher.publish(lessonRealtimeEventFactory.canceled(
                        new CanceledRealtimeContext(
                                UUID.randomUUID(),
                                now,
                                lesson.getId(),
                                instructorMemberId,
                                consumerRecipients
                        )
                ))
        );

        return LessonCancellationResponse.canceled(
                lesson.getId(),
                now.atZone(AppZoneId.SEOUL).toOffsetDateTime()
        );
    }

    private String resolveCancelReason(LessonCancellationRequest request) {
        return switch (request.cancelReason()) {
            case SCHEDULE_CHANGED -> "일정 변경";
            case INSTRUCTOR_NOT_MET -> "강사님을 못 만났어요";
            case ETC -> request.cancelReasonDetail().strip();
        };
    }
}
