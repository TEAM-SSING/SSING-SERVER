package org.sopt.ssingserver.domain.matching.realtime;

import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent.LessonSummary;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent.MatchingOfferReceivedPayload;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent.MatchingStatusPayload;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent.RequestSummary;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEventType;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeRecipientRole;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.matching.event.MatchingDomainEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferCreatedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingRequestStatusChangedEvent;
import org.sopt.ssingserver.domain.matching.realtime.MatchingNotificationContextLoader.MatchingOfferReceivedContext;
import org.sopt.ssingserver.domain.matching.realtime.MatchingNotificationContextLoader.MatchingStatusChangedContext;
import org.sopt.ssingserver.global.time.AppZoneId;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MatchingRealtimeEventFactory {

    private final MatchingNotificationContextLoader contextLoader;

    public Optional<MatchingRealtimeDelivery> create(MatchingDomainEvent event) {
        return switch (event) {
            case MatchingOfferCreatedEvent matchingOfferCreatedEvent -> create(matchingOfferCreatedEvent);
            case MatchingRequestStatusChangedEvent statusChangedEvent -> create(statusChangedEvent);
        };
    }

    private Optional<MatchingRealtimeDelivery> create(MatchingOfferCreatedEvent event) {
        return contextLoader.load(event)
                .map(context -> new MatchingRealtimeDelivery(
                        context.recipientMemberId(),
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
    }

    private Optional<MatchingRealtimeDelivery> create(MatchingRequestStatusChangedEvent event) {
        return contextLoader.load(event)
                .map(context -> createStatusChangedDelivery(event, context));
    }

    private MatchingRealtimeDelivery createStatusChangedDelivery(
            MatchingRequestStatusChangedEvent event,
            MatchingStatusChangedContext context
    ) {
        MatchingRealtimeEventType eventType = resolveStatusChangedEventType(event.matchingStatus());
        return new MatchingRealtimeDelivery(
                context.recipientMemberId(),
                new MatchingRealtimeEvent(
                        event.eventId(),
                        eventType,
                        occurredAt(event),
                        MatchingRealtimeRecipientRole.CONSUMER,
                        event.matchingRequestId(),
                        null,
                        null,
                        event.matchingStatus(),
                        new MatchingStatusPayload(
                                resolveStatusChangedMessage(event.matchingStatus()),
                                event.requestStatusReason()
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
