package org.sopt.ssingserver.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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
    void saveAndSend은_매칭_afterCommit에서도_알림함을_커밋하도록_독립_트랜잭션을_사용한다()
            throws NoSuchMethodException {
        Transactional transactional = NotificationDeliveryService.class
                .getDeclaredMethod("saveAndSend", Long.class, NotificationPayload.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

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
        stubSavedNotificationId(200L);
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
            assertThat(messageCaptor.getValue().notificationId()).isEqualTo(200L);
            assertThat(messageCaptor.getValue().token()).isEqualTo("instructor-token");
            assertThat(messageCaptor.getValue().data()).isEqualTo(payload.fcmData());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void 등록된_토큰이_없으면_커밋_이후에_FCM_발송_생략_로그를_남긴다() {
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
                .thenReturn(List.of());
        stubSavedNotificationId(201L);
        Logger logger = (Logger) LoggerFactory.getLogger(NotificationDeliveryService.class);
        Level originalLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        ListAppender<ILoggingEvent> appender = attachAppender(logger);
        TransactionSynchronizationManager.initSynchronization();

        try {
            service.saveAndSend(100L, payload);
            assertThat(appender.list).isEmpty();

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verifyNoInteractions(pushClient);
            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isSameAs(Level.INFO);
                assertThat(keyValueMap(event))
                        .containsEntry("event", "fcm.push.skipped.no_token")
                        .containsEntry("notification_id", "201")
                        .containsEntry("notification_type", payload.type().name())
                        .containsEntry("member_id", "100")
                        .containsEntry("client_app", "INSTRUCTOR");
            });
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(originalLevel);
        }
    }

    private void stubSavedNotificationId(Long notificationId) {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            ReflectionTestUtils.setField(notification, "id", notificationId);
            return notification;
        });
    }

    private ListAppender<ILoggingEvent> attachAppender(Logger logger) {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private Map<String, Object> keyValueMap(ILoggingEvent event) {
        return event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(keyValuePair -> keyValuePair.key, keyValuePair -> keyValuePair.value));
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
