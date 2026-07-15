package org.sopt.ssingserver.domain.notification.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sopt.ssingserver.domain.notification.service.FcmTokenService;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class FcmPushClientTest {

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @Mock
    private FcmTokenService fcmTokenService;

    @Test
    void send은_토큰과_DATA_only_메시지를_Firebase에_전달한다() throws Exception {
        FcmPushClient fcmPushClient = new FcmPushClient(firebaseMessaging, fcmTokenService);
        PushMessage pushMessage = new PushMessage(
                100L,
                "fcm-token",
                Map.of("type", "FCM_TEST", "title", "SSING FCM 테스트")
        );
        when(firebaseMessaging.send(any(Message.class))).thenReturn("firebase-message-id");
        Logger logger = (Logger) LoggerFactory.getLogger(FcmPushClient.class);
        Level originalLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        ListAppender<ILoggingEvent> appender = attachAppender(logger);

        try {
            fcmPushClient.send(pushMessage);

            verify(firebaseMessaging).send(any(Message.class));
            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isSameAs(Level.INFO);
                assertThat(keyValueMap(event))
                        .containsEntry("event", "fcm.push.accepted")
                        .containsEntry("provider", "firebase")
                        .containsEntry("operation", "send")
                        .containsEntry("notification_id", "100")
                        .containsEntry("notification_type", "FCM_TEST")
                        .containsEntry("fcm_message_id", "firebase-message-id");
                assertThat(keyValueMap(event).get("duration_ms")).isInstanceOf(Long.class);
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(originalLevel);
        }
    }

    @Test
    void Firebase_예외_원문은_로그에_포함하지_않는다() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(FcmPushClient.class);
        ListAppender<ILoggingEvent> appender = attachAppender(logger);

        try {
            FcmPushClient fcmPushClient = new FcmPushClient(firebaseMessaging, fcmTokenService);
            FirebaseMessagingException exception = org.mockito.Mockito.mock(FirebaseMessagingException.class);
            when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNAVAILABLE);
            when(firebaseMessaging.send(any(Message.class))).thenThrow(exception);

            fcmPushClient.send(new PushMessage(101L, "fcm-token", Map.of("type", "FCM_TEST")));

            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isSameAs(Level.WARN);
                assertThat(event.getThrowableProxy()).isNull();
                assertThat(event.getFormattedMessage()).doesNotContain("fcm-token");
                assertThat(keyValueMap(event))
                        .containsEntry("event", "fcm.push.delivery_failed")
                        .containsEntry("provider", "firebase")
                        .containsEntry("operation", "send")
                        .containsEntry("notification_id", "101")
                        .containsEntry("notification_type", "FCM_TEST")
                        .containsEntry("error_code", "UNAVAILABLE")
                        .containsEntry("exception_type", FirebaseMessagingException.class.getName());
                assertThat(keyValueMap(event).get("duration_ms")).isInstanceOf(Long.class);
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void UNREGISTERED_응답이면_무효_토큰을_삭제한다() throws Exception {
        FcmPushClient fcmPushClient = new FcmPushClient(firebaseMessaging, fcmTokenService);
        FirebaseMessagingException exception = org.mockito.Mockito.mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(firebaseMessaging.send(any(Message.class))).thenThrow(exception);

        fcmPushClient.send(new PushMessage(102L, "invalid-token", Map.of("type", "FCM_TEST")));

        verify(fcmTokenService).removeInvalidToken("invalid-token");
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
}
