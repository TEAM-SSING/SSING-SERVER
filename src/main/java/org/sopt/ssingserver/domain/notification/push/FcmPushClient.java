package org.sopt.ssingserver.domain.notification.push;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Message;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.notification.service.FcmTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class FcmPushClient implements PushClient {

    private static final Logger log = LoggerFactory.getLogger(FcmPushClient.class);
    private static final String PUSH_DELIVERY_FAILED_EVENT = "fcm.push.delivery_failed";

    private final FirebaseMessaging firebaseMessaging;
    private final FcmTokenService fcmTokenService;

    @Override
    public void send(PushMessage message) {
        // notification block 없이 Android가 직접 표시할 data-only 메시지를 HIGH priority로 전송한다.
        try {
            firebaseMessaging.send(Message.builder()
                    .setToken(message.token())
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .build())
                    .putAllData(message.data())
                    .build());
        } catch (FirebaseMessagingException exception) {
            if (exception.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                // 앱 삭제 또는 토큰 교체로 더 이상 사용할 수 없는 토큰은 다음 발송 전에 제거한다.
                fcmTokenService.removeInvalidToken(message.token());
            }

            log.atWarn()
                    .addKeyValue("event", PUSH_DELIVERY_FAILED_EVENT)
                    .addKeyValue("provider", "firebase")
                    .addKeyValue("operation", "send")
                    .addKeyValue("error_code", exception.getMessagingErrorCode())
                    .addKeyValue("exception_type", exception.getClass().getSimpleName())
                    .log("FCM push delivery failed");
        }
    }
}
