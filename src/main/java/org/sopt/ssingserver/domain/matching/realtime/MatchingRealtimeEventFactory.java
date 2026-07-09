package org.sopt.ssingserver.domain.matching.realtime;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent.InstructorAcceptedPayload;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent.InstructorSummary;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent.LessonSummary;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent.MatchingConfirmedPayload;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent.MatchingOfferClosedPayload;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent.MatchingOfferReceivedPayload;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent.MatchingStatusPayload;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent.PaymentPendingPayload;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent.PaymentStatusChangedPayload;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent.ProgressSummary;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent.RequesterConfirmationUpdatedPayload;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent.RequestSummary;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEventType;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeRecipientRole;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupItemStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
import org.sopt.ssingserver.domain.matching.event.MatchingDomainEvent;
import org.sopt.ssingserver.domain.matching.event.InstructorAcceptedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingConfirmedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferCanceledEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferClosedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferCreatedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingRequestStatusChangedEvent;
import org.sopt.ssingserver.domain.matching.event.PaymentPendingEvent;
import org.sopt.ssingserver.domain.matching.event.PaymentStatusChangedEvent;
import org.sopt.ssingserver.domain.matching.event.RequesterConfirmationUpdatedEvent;
import org.sopt.ssingserver.domain.matching.realtime.MatchingNotificationContextLoader.MatchingGroupNotificationContext;
import org.sopt.ssingserver.domain.matching.realtime.MatchingNotificationContextLoader.MatchingStatusChangedContext;
import org.sopt.ssingserver.domain.payment.enums.MatchingRequestPaymentStatus;
import org.sopt.ssingserver.global.time.AppZoneId;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MatchingRealtimeEventFactory {

    private final MatchingNotificationContextLoader contextLoader;

    public List<MatchingRealtimeDelivery> create(MatchingDomainEvent event) {
        return switch (event) {
            case MatchingOfferCreatedEvent matchingOfferCreatedEvent -> create(matchingOfferCreatedEvent);
            case MatchingRequestStatusChangedEvent statusChangedEvent -> create(statusChangedEvent);
            case InstructorAcceptedEvent instructorAcceptedEvent -> create(instructorAcceptedEvent);
            case RequesterConfirmationUpdatedEvent confirmationUpdatedEvent -> create(confirmationUpdatedEvent);
            case PaymentPendingEvent paymentPendingEvent -> create(paymentPendingEvent);
            case PaymentStatusChangedEvent paymentStatusChangedEvent -> create(paymentStatusChangedEvent);
            case MatchingConfirmedEvent matchingConfirmedEvent -> create(matchingConfirmedEvent);
            case MatchingOfferClosedEvent matchingOfferClosedEvent -> create(matchingOfferClosedEvent);
            case MatchingOfferCanceledEvent matchingOfferCanceledEvent -> create(matchingOfferCanceledEvent);
        };
    }

    private List<MatchingRealtimeDelivery> create(MatchingOfferCreatedEvent event) {
        return contextLoader.load(event)
                .map(context -> createOfferCreatedDeliveries(event, context))
                .orElseGet(List::of);
    }

    private List<MatchingRealtimeDelivery> createOfferCreatedDeliveries(
            MatchingOfferCreatedEvent event,
            MatchingGroupNotificationContext context
    ) {
        List<MatchingRealtimeDelivery> deliveries = new ArrayList<>();
        deliveries.add(new MatchingRealtimeDelivery(
                        context.instructor().recipientMemberId(),
                        new MatchingRealtimeEvent(
                                event.eventId(),
                                MatchingRealtimeEventType.MATCHING_OFFER_RECEIVED,
                                occurredAt(event),
                                MatchingRealtimeRecipientRole.INSTRUCTOR,
                                null,
                                event.matchingRequestGroupId(),
                                event.matchingOfferId(),
                                null,
                                new MatchingOfferReceivedPayload(
                                        new RequestSummary(
                                                context.requesterName(),
                                                context.headcount(),
                                                context.matchingRequestCount()
                                        ),
                                        new LessonSummary(
                                                context.resortName(),
                                                context.sport(),
                                                context.level(),
                                                context.durationMinutes(),
                                                context.totalHeadcount(),
                                                context.startType()
                                        )
                                )
                        )
                ));
        context.consumers().stream()
                .map(consumer -> createConsumerStatusDelivery(
                        event,
                        consumer.recipientMemberId(),
                        consumer.matchingRequestId(),
                        event.matchingRequestGroupId(),
                        MatchingStatus.WAITING_FOR_INSTRUCTOR,
                        "강사의 응답을 기다리고 있습니다.",
                        null
                ))
                .forEach(deliveries::add);
        return List.copyOf(deliveries);
    }

    private List<MatchingRealtimeDelivery> create(MatchingRequestStatusChangedEvent event) {
        return contextLoader.load(event)
                .map(context -> List.of(createStatusChangedDelivery(event, context)))
                .orElseGet(List::of);
    }

    private List<MatchingRealtimeDelivery> create(InstructorAcceptedEvent event) {
        return contextLoader.load(event)
                .map(context -> context.consumers().stream()
                        .map(consumer -> new MatchingRealtimeDelivery(
                                consumer.recipientMemberId(),
                                new MatchingRealtimeEvent(
                                        event.eventId(),
                                        MatchingRealtimeEventType.INSTRUCTOR_ACCEPTED,
                                        occurredAt(event),
                                        MatchingRealtimeRecipientRole.CONSUMER,
                                        consumer.matchingRequestId(),
                                        event.matchingRequestGroupId(),
                                        null,
                                        MatchingStatus.WAITING_FOR_CONFIRMATION,
                                        new InstructorAcceptedPayload(
                                                instructorSummary(context),
                                                lessonSummary(context)
                                        )
                                )
                        ))
                        .toList())
                .orElseGet(List::of);
    }

    private List<MatchingRealtimeDelivery> create(RequesterConfirmationUpdatedEvent event) {
        return contextLoader.load(event)
                .map(context -> context.consumers().stream()
                        .map(consumer -> new MatchingRealtimeDelivery(
                                consumer.recipientMemberId(),
                                new MatchingRealtimeEvent(
                                        event.eventId(),
                                        MatchingRealtimeEventType.REQUESTER_CONFIRMATION_UPDATED,
                                        occurredAt(event),
                                        MatchingRealtimeRecipientRole.CONSUMER,
                                        consumer.matchingRequestId(),
                                        event.matchingRequestGroupId(),
                                        null,
                                        confirmationStatus(consumer.confirmationStatus()),
                                        new RequesterConfirmationUpdatedPayload(new ProgressSummary(
                                                event.acceptedRequesterCount(),
                                                event.totalRequesterCount(),
                                                null
                                        ))
                                )
                        ))
                        .toList())
                .orElseGet(List::of);
    }

    private List<MatchingRealtimeDelivery> create(PaymentPendingEvent event) {
        return contextLoader.load(event)
                .map(context -> context.recipients().stream()
                        .map(recipient -> new MatchingRealtimeDelivery(
                                recipient.recipientMemberId(),
                                new MatchingRealtimeEvent(
                                        event.eventId(),
                                        MatchingRealtimeEventType.PAYMENT_PENDING,
                                        occurredAt(event),
                                        MatchingRealtimeRecipientRole.CONSUMER,
                                        recipient.matchingRequestId(),
                                        event.matchingRequestGroupId(),
                                        null,
                                        MatchingStatus.PAYMENT_PENDING,
                                        new PaymentPendingPayload(recipient.matchingRequestPaymentId())
                                )
                        ))
                        .toList())
                .orElseGet(List::of);
    }

    private List<MatchingRealtimeDelivery> create(PaymentStatusChangedEvent event) {
        return contextLoader.load(event)
                .map(context -> context.recipients().stream()
                        .map(recipient -> new MatchingRealtimeDelivery(
                                recipient.recipientMemberId(),
                                new MatchingRealtimeEvent(
                                        event.eventId(),
                                        MatchingRealtimeEventType.PAYMENT_STATUS_CHANGED,
                                        occurredAt(event),
                                        MatchingRealtimeRecipientRole.CONSUMER,
                                        recipient.matchingRequestId(),
                                        event.matchingRequestGroupId(),
                                        null,
                                        paymentStatus(recipient.paymentStatus()),
                                        new PaymentStatusChangedPayload(new ProgressSummary(
                                                null,
                                                event.totalRequesterCount(),
                                                event.paidRequesterCount()
                                        ))
                                )
                        ))
                        .toList())
                .orElseGet(List::of);
    }

    private List<MatchingRealtimeDelivery> create(MatchingConfirmedEvent event) {
        return contextLoader.load(event)
                .map(context -> createMatchingConfirmedDeliveries(event, context))
                .orElseGet(List::of);
    }

    private List<MatchingRealtimeDelivery> createMatchingConfirmedDeliveries(
            MatchingConfirmedEvent event,
            MatchingGroupNotificationContext context
    ) {
        MatchingConfirmedPayload payload = new MatchingConfirmedPayload(event.lessonId(), lessonSummary(context));
        List<MatchingRealtimeDelivery> deliveries = new ArrayList<>();
        context.consumers().stream()
                .map(consumer -> new MatchingRealtimeDelivery(
                        consumer.recipientMemberId(),
                        new MatchingRealtimeEvent(
                                event.eventId(),
                                MatchingRealtimeEventType.MATCHING_CONFIRMED,
                                occurredAt(event),
                                MatchingRealtimeRecipientRole.CONSUMER,
                                consumer.matchingRequestId(),
                                event.matchingRequestGroupId(),
                                null,
                                MatchingStatus.CONFIRMED,
                                payload
                        )
                ))
                .forEach(deliveries::add);
        deliveries.add(new MatchingRealtimeDelivery(
                context.instructor().recipientMemberId(),
                new MatchingRealtimeEvent(
                        event.eventId(),
                        MatchingRealtimeEventType.MATCHING_CONFIRMED,
                        occurredAt(event),
                        MatchingRealtimeRecipientRole.INSTRUCTOR,
                        null,
                        event.matchingRequestGroupId(),
                        event.matchingOfferId(),
                        null,
                        payload
                )
        ));
        return List.copyOf(deliveries);
    }

    private List<MatchingRealtimeDelivery> create(MatchingOfferClosedEvent event) {
        return contextLoader.load(event)
                .map(context -> List.of(new MatchingRealtimeDelivery(
                        context.recipientMemberId(),
                        new MatchingRealtimeEvent(
                                event.eventId(),
                                MatchingRealtimeEventType.MATCHING_OFFER_CLOSED,
                                occurredAt(event),
                                MatchingRealtimeRecipientRole.INSTRUCTOR,
                                null,
                                event.matchingRequestGroupId(),
                                event.matchingOfferId(),
                                null,
                                new MatchingOfferClosedPayload(
                                        event.closedReason().name(),
                                        offerClosedMessage(event)
                                )
                        )
                )))
                .orElseGet(List::of);
    }

    private List<MatchingRealtimeDelivery> create(MatchingOfferCanceledEvent event) {
        return contextLoader.load(event)
                .map(context -> List.of(new MatchingRealtimeDelivery(
                        context.recipientMemberId(),
                        new MatchingRealtimeEvent(
                                event.eventId(),
                                MatchingRealtimeEventType.MATCHING_CANCELED,
                                occurredAt(event),
                                MatchingRealtimeRecipientRole.INSTRUCTOR,
                                null,
                                event.matchingRequestGroupId(),
                                event.matchingOfferId(),
                                null,
                                new MatchingStatusPayload(
                                        "소비자가 매칭 요청을 취소했습니다.",
                                        event.requestStatusReason()
                                )
                        )
                )))
                .orElseGet(List::of);
    }

    private MatchingRealtimeDelivery createStatusChangedDelivery(
            MatchingRequestStatusChangedEvent event,
            MatchingStatusChangedContext context
    ) {
        return createConsumerStatusDelivery(
                event,
                context.recipientMemberId(),
                event.matchingRequestId(),
                event.matchingRequestGroupId(),
                event.matchingStatus(),
                resolveStatusChangedMessage(event.matchingStatus()),
                event.requestStatusReason()
        );
    }

    private MatchingRealtimeDelivery createConsumerStatusDelivery(
            MatchingDomainEvent event,
            Long recipientMemberId,
            Long matchingRequestId,
            Long groupId,
            MatchingStatus matchingStatus,
            String message,
            MatchingRequestStatusReason requestStatusReason
    ) {
        MatchingRealtimeEventType eventType = resolveStatusChangedEventType(matchingStatus);
        return new MatchingRealtimeDelivery(
                recipientMemberId,
                new MatchingRealtimeEvent(
                        event.eventId(),
                        eventType,
                        occurredAt(event),
                        MatchingRealtimeRecipientRole.CONSUMER,
                        matchingRequestId,
                        groupId,
                        null,
                        matchingStatus,
                        new MatchingStatusPayload(
                                message,
                                requestStatusReason
                        )
                )
        );
    }

    private MatchingRealtimeEventType resolveStatusChangedEventType(MatchingStatus matchingStatus) {
        if (matchingStatus == MatchingStatus.CANCELED) {
            return MatchingRealtimeEventType.MATCHING_CANCELED;
        }
        if (matchingStatus == MatchingStatus.FAILED
                || matchingStatus == MatchingStatus.NO_AVAILABLE_INSTRUCTOR
                || matchingStatus == MatchingStatus.PAYMENT_EXPIRED) {
            return MatchingRealtimeEventType.MATCHING_FAILED;
        }
        return MatchingRealtimeEventType.MATCHING_STATUS_CHANGED;
    }

    private MatchingStatus confirmationStatus(MatchingRequestGroupItemStatus status) {
        if (status == MatchingRequestGroupItemStatus.ACCEPTED) {
            return MatchingStatus.WAITING_FOR_OTHER_CONFIRMATIONS;
        }
        return MatchingStatus.WAITING_FOR_CONFIRMATION;
    }

    private MatchingStatus paymentStatus(MatchingRequestPaymentStatus status) {
        if (status == MatchingRequestPaymentStatus.COMPLETED) {
            return MatchingStatus.WAITING_FOR_OTHER_PAYMENTS;
        }
        return MatchingStatus.PAYMENT_PENDING;
    }

    private InstructorSummary instructorSummary(MatchingGroupNotificationContext context) {
        return new InstructorSummary(
                context.instructor().instructorProfileId(),
                context.instructor().name(),
                context.instructor().profileImageUrl()
        );
    }

    private LessonSummary lessonSummary(MatchingGroupNotificationContext context) {
        return new LessonSummary(
                context.resortName(),
                context.sport(),
                context.level(),
                context.durationMinutes(),
                context.totalHeadcount(),
                context.startType()
        );
    }

    private String offerClosedMessage(MatchingOfferClosedEvent event) {
        return switch (event.closedReason()) {
            case REJECTED -> "거절한 매칭 제안이 종료되었습니다.";
            case EXPIRED -> "응답 시간이 지나 매칭 제안이 종료되었습니다.";
            case ACCEPTED_BY_OTHER_INSTRUCTOR -> "다른 강사가 먼저 수락해 매칭 제안이 종료되었습니다.";
            case GROUP_CANCELED -> "매칭 그룹이 취소되어 제안이 종료되었습니다.";
        };
    }

    private String resolveStatusChangedMessage(MatchingStatus matchingStatus) {
        return switch (matchingStatus) {
            case CANCELED -> "매칭 요청이 취소되었습니다.";
            case NO_AVAILABLE_INSTRUCTOR -> "조건에 맞는 강사를 찾지 못했습니다.";
            case FAILED, PAYMENT_EXPIRED -> "매칭이 종료되었습니다.";
            default -> "매칭 상태가 변경되었습니다.";
        };
    }

    private OffsetDateTime occurredAt(MatchingDomainEvent event) {
        return event.occurredAt()
                .atZone(AppZoneId.SEOUL)
                .toOffsetDateTime();
    }
}
