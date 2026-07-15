package org.sopt.ssingserver.domain.notification.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferClosedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferClosedReason;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferCreatedEvent;

@ExtendWith(MockitoExtension.class)
class MatchingPushNotificationPublisherTest {

    @Mock
    private MatchingPushRecipientResolver matchingPushRecipientResolver;

    @Mock
    private NotificationDeliveryService notificationDeliveryService;

    @Test
    void 새_강습_도착_이벤트를_강사_알림함_저장과_FCM_발송으로_연결한다() {
        MatchingOfferCreatedEvent event = offerCreatedEvent();
        NotificationPayloadFactory factory = new NotificationPayloadFactory();
        MatchingPushNotificationPublisher publisher = new MatchingPushNotificationPublisher(
                factory,
                matchingPushRecipientResolver,
                notificationDeliveryService
        );
        when(matchingPushRecipientResolver.findInstructorMemberId(event.matchingOfferId()))
                .thenReturn(Optional.of(100L));

        publisher.handle(event);

        verify(notificationDeliveryService).saveAndSend(
                org.mockito.ArgumentMatchers.eq(100L),
                org.mockito.ArgumentMatchers.argThat(payload -> payload.type().name().equals("MATCHING_OFFER_RECEIVED"))
        );
    }

    @Test
    void 소비자_거절이_아닌_제안_종료는_알림을_발송하지_않는다() {
        MatchingPushNotificationPublisher publisher = new MatchingPushNotificationPublisher(
                new NotificationPayloadFactory(),
                matchingPushRecipientResolver,
                notificationDeliveryService
        );

        publisher.handle(new MatchingOfferClosedEvent(
                UUID.randomUUID(),
                Instant.parse("2026-07-16T00:00:00Z"),
                20L,
                30L,
                MatchingOfferClosedReason.EXPIRED
        ));

        verifyNoInteractions(matchingPushRecipientResolver, notificationDeliveryService);
    }

    private MatchingOfferCreatedEvent offerCreatedEvent() {
        return new MatchingOfferCreatedEvent(
                UUID.randomUUID(),
                Instant.parse("2026-07-16T00:00:00Z"),
                10L,
                20L,
                30L,
                120,
                40L
        );
    }
}
