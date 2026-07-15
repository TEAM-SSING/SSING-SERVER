package org.sopt.ssingserver.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferCreatedEvent;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.member.repository.MemberRepository;
import org.sopt.ssingserver.domain.notification.entity.FcmToken;
import org.sopt.ssingserver.domain.notification.entity.Notification;
import org.sopt.ssingserver.domain.notification.enums.ClientApp;
import org.sopt.ssingserver.domain.notification.enums.ClientPlatform;
import org.sopt.ssingserver.domain.notification.push.PushClient;
import org.sopt.ssingserver.domain.notification.push.PushMessage;
import org.sopt.ssingserver.domain.notification.repository.FcmTokenRepository;
import org.sopt.ssingserver.domain.notification.repository.NotificationRepository;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private FcmTokenRepository fcmTokenRepository;

    @Mock
    private PushClient pushClient;

    @Test
    void 알림함을_저장한_커밋_이후에_등록된_강사_토큰으로_FCM을_발송한다() {
        Member instructor = Member.create("강사", null, MemberRole.INSTRUCTOR, MemberStatus.ACTIVE);
        NotificationPayload payload = new NotificationPayloadFactory().create(offerCreatedEvent()).orElseThrow();
        NotificationDeliveryService service = new NotificationDeliveryService(
                memberRepository,
                notificationRepository,
                fcmTokenRepository,
                pushClient,
                new ObjectMapper()
        );
        when(memberRepository.getReferenceById(100L)).thenReturn(instructor);
        when(fcmTokenRepository.findAllByMemberIdAndClientApp(100L, ClientApp.INSTRUCTOR))
                .thenReturn(List.of(FcmToken.create(
                        instructor,
                        ClientApp.INSTRUCTOR,
                        ClientPlatform.ANDROID,
                        "instructor-token",
                        Instant.parse("2026-07-16T00:00:00Z")
                )));
        TransactionSynchronizationManager.initSynchronization();

        try {
            service.saveAndSend(100L, payload);

            ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(notificationCaptor.capture());
            assertThat(notificationCaptor.getValue().getType()).isEqualTo(payload.type());
            assertThat(notificationCaptor.getValue().getDataJson())
                    .contains("https://ssing.app/instructor-matching", "offerId", "30");
            verifyNoInteractions(pushClient);

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            ArgumentCaptor<PushMessage> messageCaptor = ArgumentCaptor.forClass(PushMessage.class);
            verify(pushClient).send(messageCaptor.capture());
            assertThat(messageCaptor.getValue().token()).isEqualTo("instructor-token");
            assertThat(messageCaptor.getValue().data()).isEqualTo(payload.fcmData());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
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
