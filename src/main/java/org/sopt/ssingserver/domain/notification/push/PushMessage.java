package org.sopt.ssingserver.domain.notification.push;

import java.util.Map;

public record PushMessage(
        Long notificationId,
        String token,
        Map<String, String> data
) {
}
