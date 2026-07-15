package org.sopt.ssingserver.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.matching.event.MatchingConfirmedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferClosedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferClosedReason;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferCreatedEvent;
import org.sopt.ssingserver.domain.notification.enums.ClientApp;
import org.sopt.ssingserver.domain.notification.enums.NotificationType;

class NotificationPayloadFactoryTest {

    private final NotificationPayloadFactory factory = new NotificationPayloadFactory();

    @Test
    void 새_강습_도착은_offerId를_포함한_data_only_payload를_만든다() {
        NotificationPayload payload = factory.create(offerCreatedEvent()).orElseThrow();

        assertThat(payload.type()).isEqualTo(NotificationType.MATCHING_OFFER_RECEIVED);
        assertThat(payload.clientApp()).isEqualTo(ClientApp.INSTRUCTOR);
        assertThat(payload.fcmData())
                .containsEntry("type", "MATCHING_OFFER_RECEIVED")
                .containsEntry("deepLink", "https://ssing.app/instructor-matching")
                .containsEntry("offerId", "30");
        assertThat(payload.notificationData())
                .containsEntry("deepLink", "https://ssing.app/instructor-matching")
                .containsEntry("offerId", "30");
    }

    @Test
    void 소비자_거절로_제안이_종료되면_매칭_화면으로_이동하는_payload를_만든다() {
        NotificationPayload payload = factory.create(offerClosedEvent(MatchingOfferClosedReason.GROUP_CANCELED))
                .orElseThrow();

        assertThat(payload.type()).isEqualTo(NotificationType.MATCHING_OFFER_CLOSED);
        assertThat(payload.clientApp()).isEqualTo(ClientApp.INSTRUCTOR);
        assertThat(payload.fcmData())
                .containsEntry("type", "MATCHING_OFFER_CLOSED")
                .containsEntry("deepLink", "https://ssing.app/instructor-matching")
                .containsEntry("offerId", "30");
        assertThat(payload.notificationData()).containsEntry("offerId", "30");
    }

    @Test
    void 소비자_거절이_아닌_제안_종료는_푸시_알림을_만들지_않는다() {
        assertThat(factory.create(offerClosedEvent(MatchingOfferClosedReason.EXPIRED))).isEmpty();
    }

    @Test
    void 강습_확정은_lessonId가_포함된_앱_링크를_만든다() {
        NotificationPayload payload = factory.create(new MatchingConfirmedEvent(
                UUID.randomUUID(),
                Instant.parse("2026-07-16T00:00:00Z"),
                20L,
                30L,
                40L
        )).orElseThrow();

        assertThat(payload.type()).isEqualTo(NotificationType.MATCHING_CONFIRMED);
        assertThat(payload.clientApp()).isEqualTo(ClientApp.INSTRUCTOR);
        assertThat(payload.fcmData())
                .containsEntry("deepLink", "https://ssing.app/instructor-lesson/40")
                .containsEntry("lessonId", "40");
        assertThat(payload.notificationData()).containsEntry("lessonId", "40");
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

    private MatchingOfferClosedEvent offerClosedEvent(MatchingOfferClosedReason reason) {
        return new MatchingOfferClosedEvent(
                UUID.randomUUID(),
                Instant.parse("2026-07-16T00:00:00Z"),
                20L,
                30L,
                reason
        );
    }
}
