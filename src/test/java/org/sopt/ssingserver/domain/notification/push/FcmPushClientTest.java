package org.sopt.ssingserver.domain.notification.push;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class FcmPushClientTest {

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @Test
    void send은_토큰과_DATA_only_메시지를_Firebase에_전달한다() throws Exception {
        FcmPushClient fcmPushClient = new FcmPushClient(firebaseMessaging);
        PushMessage pushMessage = new PushMessage(
                "fcm-token",
                Map.of("type", "FCM_TEST", "title", "SSING FCM 테스트")
        );
        when(firebaseMessaging.send(any(Message.class))).thenReturn("firebase-message-id");

        fcmPushClient.send(pushMessage);

        verify(firebaseMessaging).send(any(Message.class));
    }

    @Test
    void Firebase_예외_원문은_로그에_포함하지_않는다() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(FcmPushClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            FcmPushClient fcmPushClient = new FcmPushClient(firebaseMessaging);
            FirebaseMessagingException exception = org.mockito.Mockito.mock(FirebaseMessagingException.class);
            when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNAVAILABLE);
            when(firebaseMessaging.send(any(Message.class))).thenThrow(exception);

            fcmPushClient.send(new PushMessage("fcm-token", Map.of("type", "FCM_TEST")));

            assertThat(appender.list).singleElement().satisfies(event ->
                    assertThat(event.getThrowableProxy()).isNull()
            );
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
