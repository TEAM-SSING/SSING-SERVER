package org.sopt.ssingserver.domain.notification.push;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
