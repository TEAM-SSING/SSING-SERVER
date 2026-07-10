package org.sopt.ssingserver.domain.lesson.realtime;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.sopt.ssingserver.domain.lesson.dto.realtime.LessonRealtimeEvent;
import org.sopt.ssingserver.domain.lesson.dto.realtime.LessonRealtimeEvent.StartConfirmationUpdatedPayload;
import org.sopt.ssingserver.domain.lesson.dto.realtime.LessonRealtimeEventType;
import org.sopt.ssingserver.domain.lesson.dto.realtime.LessonRealtimeRecipientRole;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.global.time.AppZoneId;
import org.springframework.stereotype.Component;

@Component
public class LessonRealtimeEventFactory {

    public List<LessonRealtimeDelivery> startConfirmationUpdated(
            StartConfirmationRealtimeContext context
    ) {
        boolean instructorConfirmed = context.instructorConfirmed();
        Set<Long> sentMemberIds = new LinkedHashSet<>();
        List<LessonRealtimeDelivery> deliveries = new java.util.ArrayList<>();

        sentMemberIds.add(context.instructorMemberId());
        deliveries.add(new LessonRealtimeDelivery(
                context.instructorMemberId(),
                new LessonRealtimeEvent(
                        context.eventId(),
                        LessonRealtimeEventType.LESSON_START_CONFIRMATION_UPDATED,
                        toOffsetDateTime(context.occurredAt()),
                        LessonRealtimeRecipientRole.INSTRUCTOR,
                        context.lessonId(),
                        LessonStatus.CONFIRMED,
                        new StartConfirmationUpdatedPayload(
                                context.confirmedCount(),
                                context.requiredCount(),
                                instructorConfirmed,
                                instructorConfirmed
                        )
                )
        ));

        for (ConsumerRecipient recipient : context.consumerRecipients()) {
            if (!sentMemberIds.add(recipient.memberId())) {
                continue;
            }
            deliveries.add(new LessonRealtimeDelivery(
                    recipient.memberId(),
                    new LessonRealtimeEvent(
                            context.eventId(),
                            LessonRealtimeEventType.LESSON_START_CONFIRMATION_UPDATED,
                            toOffsetDateTime(context.occurredAt()),
                            LessonRealtimeRecipientRole.CONSUMER,
                            context.lessonId(),
                            LessonStatus.CONFIRMED,
                            new StartConfirmationUpdatedPayload(
                                    context.confirmedCount(),
                                    context.requiredCount(),
                                    context.confirmedMatchingRequestIds().contains(recipient.matchingRequestId()),
                                    instructorConfirmed
                            )
                    )
            ));
        }
        return deliveries;
    }

    public List<LessonRealtimeDelivery> started(StartedRealtimeContext context) {
        return statusChanged(
                context.eventId(),
                context.occurredAt(),
                context.lessonId(),
                context.instructorMemberId(),
                context.consumerRecipients(),
                LessonRealtimeEventType.LESSON_STARTED,
                LessonStatus.IN_PROGRESS
        );
    }

    public List<LessonRealtimeDelivery> completed(CompletedRealtimeContext context) {
        return statusChanged(
                context.eventId(),
                context.occurredAt(),
                context.lessonId(),
                context.instructorMemberId(),
                context.consumerRecipients(),
                LessonRealtimeEventType.LESSON_COMPLETED,
                LessonStatus.COMPLETED
        );
    }

    public List<LessonRealtimeDelivery> canceled(CanceledRealtimeContext context) {
        return statusChanged(
                context.eventId(),
                context.occurredAt(),
                context.lessonId(),
                context.instructorMemberId(),
                context.consumerRecipients(),
                LessonRealtimeEventType.LESSON_CANCELED,
                LessonStatus.CANCELED
        );
    }

    private List<LessonRealtimeDelivery> statusChanged(
            UUID eventId,
            Instant occurredAt,
            Long lessonId,
            Long instructorMemberId,
            List<ConsumerRecipient> consumerRecipients,
            LessonRealtimeEventType eventType,
            LessonStatus lessonStatus
    ) {
        Set<Long> sentMemberIds = new LinkedHashSet<>();
        List<LessonRealtimeDelivery> deliveries = new java.util.ArrayList<>();

        sentMemberIds.add(instructorMemberId);
        deliveries.add(statusChangedDelivery(
                eventId,
                occurredAt,
                lessonId,
                instructorMemberId,
                LessonRealtimeRecipientRole.INSTRUCTOR,
                eventType,
                lessonStatus
        ));

        for (ConsumerRecipient recipient : consumerRecipients) {
            if (sentMemberIds.add(recipient.memberId())) {
                deliveries.add(statusChangedDelivery(
                        eventId,
                        occurredAt,
                        lessonId,
                        recipient.memberId(),
                        LessonRealtimeRecipientRole.CONSUMER,
                        eventType,
                        lessonStatus
                ));
            }
        }
        return deliveries;
    }

    private LessonRealtimeDelivery statusChangedDelivery(
            UUID eventId,
            Instant occurredAt,
            Long lessonId,
            Long recipientMemberId,
            LessonRealtimeRecipientRole recipientRole,
            LessonRealtimeEventType eventType,
            LessonStatus lessonStatus
    ) {
        return new LessonRealtimeDelivery(
                recipientMemberId,
                new LessonRealtimeEvent(
                        eventId,
                        eventType,
                        toOffsetDateTime(occurredAt),
                        recipientRole,
                        lessonId,
                        lessonStatus,
                        Map.of()
                )
        );
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atZone(AppZoneId.SEOUL).toOffsetDateTime();
    }

    public record StartConfirmationRealtimeContext(
            UUID eventId,
            Instant occurredAt,
            Long lessonId,
            Long instructorMemberId,
            List<ConsumerRecipient> consumerRecipients,
            int confirmedCount,
            int requiredCount,
            boolean instructorConfirmed,
            Set<Long> confirmedMatchingRequestIds
    ) {
    }

    public record StartedRealtimeContext(
            UUID eventId,
            Instant occurredAt,
            Long lessonId,
            Long instructorMemberId,
            List<ConsumerRecipient> consumerRecipients
    ) {
    }

    public record CompletedRealtimeContext(
            UUID eventId,
            Instant occurredAt,
            Long lessonId,
            Long instructorMemberId,
            List<ConsumerRecipient> consumerRecipients
    ) {
    }

    public record CanceledRealtimeContext(
            UUID eventId,
            Instant occurredAt,
            Long lessonId,
            Long instructorMemberId,
            List<ConsumerRecipient> consumerRecipients
    ) {
    }

    public record ConsumerRecipient(
            Long memberId,
            Long matchingRequestId,
            Member member,
            MatchingRequest matchingRequest
    ) {
    }
}
