package org.sopt.ssingserver.domain.notification.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sopt.ssingserver.domain.matching.event.MatchingConfirmedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingDomainEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingEventHandler;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferClosedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferCreatedEvent;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
@RequiredArgsConstructor
public class MatchingPushNotificationPublisher implements MatchingEventHandler {

    private static final Logger log = LoggerFactory.getLogger(MatchingPushNotificationPublisher.class);

    private final NotificationPayloadFactory notificationPayloadFactory;
    private final MatchingPushRecipientResolver matchingPushRecipientResolver;
    private final NotificationDeliveryService notificationDeliveryService;

    @Override
    public void handle(MatchingDomainEvent event) {
        // 지원하는 매칭 이벤트만 알림 payload로 변환해 수신자 조회 단계로 넘긴다.
        notificationPayloadFactory.create(event)
                .ifPresent(payload -> resolveRecipientAndDeliver(event, payload));
    }

    private void resolveRecipientAndDeliver(MatchingDomainEvent event, NotificationPayload payload) {
        // 실제 수신자는 payload의 ID가 아니라 매칭 제안에 연결된 강사 회원으로 결정한다.
        matchingPushRecipientResolver.findInstructorMemberId(matchingOfferId(event))
                .ifPresentOrElse(
                        memberId -> notificationDeliveryService.saveAndSend(memberId, payload),
                        () -> logSkippedEvent(event)
                );
    }

    private Long matchingOfferId(MatchingDomainEvent event) {
        // 세 알림 이벤트 모두 수신 강사를 찾는 기준은 원본 matchingOfferId다.
        return switch (event) {
            case MatchingOfferCreatedEvent offerCreatedEvent -> offerCreatedEvent.matchingOfferId();
            case MatchingOfferClosedEvent offerClosedEvent -> offerClosedEvent.matchingOfferId();
            case MatchingConfirmedEvent matchingConfirmedEvent -> matchingConfirmedEvent.matchingOfferId();
            default -> throw new IllegalArgumentException("Unsupported notification event.");
        };
    }

    private void logSkippedEvent(MatchingDomainEvent event) {
        // 삭제되었거나 비정상적인 제안 참조는 발송을 중단하고 추적 가능한 로그만 남긴다.
        log.atWarn()
                .addKeyValue("event", "notification.matching.publish.skipped")
                .addKeyValue("matching_event_id", event.eventId().toString())
                .addKeyValue("matching_event_type", event.getClass().getSimpleName())
                .log("Matching notification recipient was not found");
    }
}
