package org.sopt.ssingserver.domain.lesson.service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.lesson.dto.response.LessonStartConfirmationResponse;
import org.sopt.ssingserver.domain.lesson.dto.response.LessonStartConfirmationResponse.StatusInfoResponse;
import org.sopt.ssingserver.domain.lesson.entity.Lesson;
import org.sopt.ssingserver.domain.lesson.entity.LessonStartConfirmation;
import org.sopt.ssingserver.domain.lesson.enums.LessonStartConfirmationActor;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;
import org.sopt.ssingserver.domain.lesson.error.LessonErrorCode;
import org.sopt.ssingserver.domain.lesson.realtime.LessonRealtimeDelivery;
import org.sopt.ssingserver.domain.lesson.realtime.LessonRealtimeEventFactory;
import org.sopt.ssingserver.domain.lesson.realtime.LessonRealtimeEventFactory.ConsumerRecipient;
import org.sopt.ssingserver.domain.lesson.realtime.LessonRealtimeEventFactory.StartConfirmationRealtimeContext;
import org.sopt.ssingserver.domain.lesson.realtime.LessonRealtimeEventFactory.StartedRealtimeContext;
import org.sopt.ssingserver.domain.lesson.realtime.LessonRealtimeEventPublisher;
import org.sopt.ssingserver.domain.lesson.repository.LessonParticipantRepository;
import org.sopt.ssingserver.domain.lesson.repository.LessonRepository;
import org.sopt.ssingserver.domain.lesson.repository.LessonStartConfirmationRepository;
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
public class LessonStartConfirmationService {

    private final LessonRepository lessonRepository;
    private final LessonParticipantRepository lessonParticipantRepository;
    private final LessonStartConfirmationRepository lessonStartConfirmationRepository;
    private final LessonRealtimeEventFactory lessonRealtimeEventFactory;
    private final LessonRealtimeEventPublisher lessonRealtimeEventPublisher;
    private final LessonAfterCommitExecutor lessonAfterCommitExecutor;
    private final Clock clock;

    @Transactional
    public LessonStartConfirmationResponse confirmStart(
            CurrentMember currentMember,
            Long lessonId
    ) {
        // 강습 조회 및 잠금
        Lesson lesson = lessonRepository.findByIdForUpdate(lessonId)
                .orElseThrow(() -> new BusinessException(LessonErrorCode.LESSON_NOT_FOUND));

        // 실제 강습 참여 팀과 확인 대상 정리
        List<MatchingRequest> matchingRequests =
                lessonParticipantRepository.findDistinctMatchingRequestsByLessonId(lessonId);
        LessonRecipients recipients = recipients(lesson, matchingRequests);

        // 현재 요청자의 역할과 권한 판별
        ActorContext actorContext = resolveActor(currentMember, lesson, recipients);

        // 강습 상태 검사
        if (lesson.getStatus() == LessonStatus.IN_PROGRESS) {
            return handleInProgressRetry(lesson, currentMember.memberId(), actorContext, recipients);
        }
        if (lesson.getStatus() != LessonStatus.CONFIRMED) {
            throw new BusinessException(LessonErrorCode.LESSON_START_NOT_ALLOWED);
        }

        // 중복 강습 준비 저장 방지
        Instant now = clock.instant();
        lessonStartConfirmationRepository.findByLessonIdAndMemberId(lessonId, currentMember.memberId())
                .orElseGet(() -> lessonStartConfirmationRepository.save(actorContext.createConfirmation(lesson, now)));

        // 전체 강습 준비 현황 계산
        List<LessonStartConfirmation> confirmations = lessonStartConfirmationRepository.findByLessonId(lessonId);
        ConfirmationSummary summary = summarize(lesson, recipients, confirmations, actorContext);

        // 전체 준비 여부에 따라 분기
        if (summary.completed()) {
            lesson.start(now);
            LessonStartConfirmationResponse response = LessonStartConfirmationResponse.started(
                    lesson.getId(),
                    toOffsetDateTime(now)
            );
            publishAfterCommit(lessonRealtimeEventFactory.started(new StartedRealtimeContext(
                    UUID.randomUUID(),
                    now,
                    lesson.getId(),
                    recipients.instructorMemberId(),
                    recipients.consumerRecipients()
            )));
            return response;
        }

        LessonStartConfirmationResponse response = LessonStartConfirmationResponse.pending(
                lesson.getId(),
                new StatusInfoResponse(
                        summary.confirmedCount(),
                        summary.requiredCount(),
                        summary.currentActorConfirmed(),
                        summary.instructorConfirmed()
                )
        );

        // 웹소켓 전송
        publishAfterCommit(lessonRealtimeEventFactory.startConfirmationUpdated(new StartConfirmationRealtimeContext(
                UUID.randomUUID(),
                now,
                lesson.getId(),
                recipients.instructorMemberId(),
                recipients.consumerRecipients(),
                summary.confirmedCount(),
                summary.requiredCount(),
                summary.instructorConfirmed(),
                summary.confirmedMatchingRequestIds()
        )));
        return response;
    }

    private LessonStartConfirmationResponse handleInProgressRetry(
            Lesson lesson,
            Long memberId,
            ActorContext actorContext,
            LessonRecipients recipients
    ) {
        if (lessonStartConfirmationRepository.findByLessonIdAndMemberId(lesson.getId(), memberId).isEmpty()) {
            throw new BusinessException(LessonErrorCode.LESSON_START_NOT_ALLOWED);
        }
        Instant startedAt = lesson.getStartedAt();
        if (startedAt == null) {
            throw new BusinessException(LessonErrorCode.LESSON_INVALID_STATE);
        }
        return LessonStartConfirmationResponse.started(lesson.getId(), toOffsetDateTime(startedAt));
    }

    private ActorContext resolveActor(
            CurrentMember currentMember,
            Lesson lesson,
            LessonRecipients recipients
    ) {
        if (currentMember.isApprovedInstructor()
                && Objects.equals(recipients.instructorMemberId(), currentMember.memberId())) {
            return ActorContext.instructor(lesson.getInstructorProfile().getMember());
        }
        if (currentMember.isActiveConsumer()) {
            for (ConsumerRecipient recipient : recipients.consumerRecipients()) {
                if (Objects.equals(recipient.memberId(), currentMember.memberId())) {
                    return ActorContext.consumer(recipient.member(), recipient.matchingRequest());
                }
            }
        }
        throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }

    private LessonRecipients recipients(
            Lesson lesson,
            List<MatchingRequest> matchingRequests
    ) {
        List<ConsumerRecipient> consumerRecipients = matchingRequests.stream()
                .map(matchingRequest -> new ConsumerRecipient(
                        matchingRequest.getMember().getId(),
                        matchingRequest.getId(),
                        matchingRequest.getMember(),
                        matchingRequest
                ))
                .toList();
        return new LessonRecipients(
                lesson.getInstructorProfile().getMember().getId(),
                consumerRecipients
        );
    }

    private ConfirmationSummary summarize(
            Lesson lesson,
            LessonRecipients recipients,
            List<LessonStartConfirmation> confirmations,
            ActorContext currentActor
    ) {
        boolean instructorConfirmed = false;
        Set<Long> confirmedMatchingRequestIds = new LinkedHashSet<>();
        for (LessonStartConfirmation confirmation : confirmations) {
            if (confirmation.getActorType() == LessonStartConfirmationActor.INSTRUCTOR) {
                instructorConfirmed = true;
            }
            if (confirmation.getActorType() == LessonStartConfirmationActor.CONSUMER
                    && confirmation.getMatchingRequest() != null) {
                confirmedMatchingRequestIds.add(confirmation.getMatchingRequest().getId());
            }
        }

        int confirmedConsumerCount = confirmations.stream()
                .filter(confirmation -> confirmation.getActorType() == LessonStartConfirmationActor.CONSUMER)
                .map(LessonStartConfirmation::getMatchingRequest)
                .filter(Objects::nonNull)
                .mapToInt(MatchingRequest::getHeadcount)
                .sum();
        int confirmedCount = confirmedConsumerCount + (instructorConfirmed ? 1 : 0);
        int requiredCount = lesson.getTotalHeadcount() + 1;
        boolean currentActorConfirmed = currentActor.isConfirmed(instructorConfirmed, confirmedMatchingRequestIds);
        return new ConfirmationSummary(
                confirmedCount,
                requiredCount,
                currentActorConfirmed,
                instructorConfirmed,
                confirmedMatchingRequestIds
        );
    }

    private void publishAfterCommit(List<LessonRealtimeDelivery> deliveries) {
        lessonAfterCommitExecutor.execute(
                "lesson-realtime-event-publish",
                () -> lessonRealtimeEventPublisher.publish(deliveries)
        );
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atZone(AppZoneId.SEOUL).toOffsetDateTime();
    }

    // 서비스 내부 계산용 record
    private record LessonRecipients(
            Long instructorMemberId,
            List<ConsumerRecipient> consumerRecipients
    ) {
    }

    private record ActorContext(
            LessonStartConfirmationActor actorType,
            Member member,
            MatchingRequest matchingRequest
    ) {

        static ActorContext instructor(Member member) {
            return new ActorContext(LessonStartConfirmationActor.INSTRUCTOR, member, null);
        }

        static ActorContext consumer(
                Member member,
                MatchingRequest matchingRequest
        ) {
            return new ActorContext(LessonStartConfirmationActor.CONSUMER, member, matchingRequest);
        }

        LessonStartConfirmation createConfirmation(
                Lesson lesson,
                Instant confirmedAt
        ) {
            if (actorType == LessonStartConfirmationActor.INSTRUCTOR) {
                return LessonStartConfirmation.confirmInstructor(lesson, member, confirmedAt);
            }
            return LessonStartConfirmation.confirmConsumer(lesson, member, matchingRequest, confirmedAt);
        }

        boolean isConfirmed(
                boolean instructorConfirmed,
                Set<Long> confirmedMatchingRequestIds
        ) {
            if (actorType == LessonStartConfirmationActor.INSTRUCTOR) {
                return instructorConfirmed;
            }
            return confirmedMatchingRequestIds.contains(matchingRequest.getId());
        }
    }

    private record ConfirmationSummary(
            int confirmedCount,
            int requiredCount,
            boolean currentActorConfirmed,
            boolean instructorConfirmed,
            Set<Long> confirmedMatchingRequestIds
    ) {

        boolean completed() {
            return confirmedCount == requiredCount;
        }
    }
}
