package org.sopt.ssingserver.domain.notification.push;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class FcmPushClient implements PushClient {

    private static final Logger log = LoggerFactory.getLogger(FcmPushClient.class);

    private final FirebaseMessaging firebaseMessaging;

    @Override
    public void send(PushMessage message) {
        try {
            firebaseMessaging.send(Message.builder()
                    .setToken(message.token())
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .build())
                    .putAllData(message.data())
                    .build());
        } catch (FirebaseMessagingException exception) {
            log.atWarn()
                    .addKeyValue("event", "fcm.push.delivery_failed")
                    .addKeyValue("errorCode", exception.getMessagingErrorCode())
                    .setCause(exception)
                    .log("FCM push delivery failed");
        }
    }
}
