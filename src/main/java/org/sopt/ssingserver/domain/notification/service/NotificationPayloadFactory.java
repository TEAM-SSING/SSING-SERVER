package org.sopt.ssingserver.domain.notification.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.sopt.ssingserver.domain.matching.event.MatchingConfirmedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingDomainEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferClosedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferClosedReason;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferCreatedEvent;
import org.sopt.ssingserver.domain.notification.definition.NotificationDefinition;
import org.sopt.ssingserver.domain.notification.definition.NotificationDefinition.Target;
import org.springframework.stereotype.Component;

@Component
public class NotificationPayloadFactory {

    public Optional<NotificationPayload> create(MatchingDomainEvent event) {
        // 현재 이벤트가 푸시 대상인지 판단하고, 대상이면 타입별 발송/저장 데이터를 함께 조립한다.
        return switch (event) {
            case MatchingOfferCreatedEvent offerCreatedEvent -> Optional.of(create(
                    NotificationDefinition.MATCHING_OFFER_RECEIVED,
                    offerCreatedEvent.matchingOfferId()
            ));
            case MatchingOfferClosedEvent offerClosedEvent -> matchingOfferClosed(offerClosedEvent);
            case MatchingConfirmedEvent matchingConfirmedEvent -> Optional.of(create(
                    NotificationDefinition.MATCHING_CONFIRMED,
                    matchingConfirmedEvent.lessonId()
            ));
            default -> Optional.empty();
        };
    }

    private Optional<NotificationPayload> matchingOfferClosed(MatchingOfferClosedEvent event) {
        // 강사 본인 거절이나 시간 만료는 알림 대상이 아니고, 소비자 거절로 그룹이 끝난 경우만 안내한다.
        if (event.closedReason() != MatchingOfferClosedReason.GROUP_CANCELED) {
            return Optional.empty();
        }

        return Optional.of(create(NotificationDefinition.MATCHING_OFFER_CLOSED, event.matchingOfferId()));
    }

    private NotificationPayload create(NotificationDefinition definition, Long targetId) {
        // FCM data와 알림함 JSON은 쓰임새가 달라 각각 필요한 데이터만 별도로 만든다.
        String targetValue = String.valueOf(targetId);
        String deepLink = definition.deepLink(targetId);
        Map<String, String> fcmData = new LinkedHashMap<>();
        fcmData.put("type", definition.type().name());
        fcmData.put("title", definition.title());
        fcmData.put("body", definition.body());
        fcmData.put("deepLink", deepLink);
        addTarget(fcmData, definition.fcmTarget(), targetValue);

        Map<String, String> notificationData = new LinkedHashMap<>();
        notificationData.put("deepLink", deepLink);
        addTarget(notificationData, definition.storedTarget(), targetValue);

        return new NotificationPayload(
                definition.type(),
                definition.clientApp(),
                definition.title(),
                definition.body(),
                Map.copyOf(fcmData),
                Map.copyOf(notificationData)
        );
    }

    private void addTarget(Map<String, String> data, Target target, String targetValue) {
        // 대상이 없는 알림은 식별자 key 자체를 생략해 null 또는 빈 문자열 계약을 만들지 않는다.
        if (target != Target.NONE) {
            data.put(target.dataKey(), targetValue);
        }
    }
}
