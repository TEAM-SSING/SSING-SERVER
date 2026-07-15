package org.sopt.ssingserver.domain.notification.service;

import java.util.Map;
import org.sopt.ssingserver.domain.notification.enums.ClientApp;
import org.sopt.ssingserver.domain.notification.enums.NotificationType;

public record NotificationPayload(
        NotificationType type,
        ClientApp clientApp,
        String title,
        String body,
        Map<String, String> fcmData,
        Map<String, String> notificationData
) {
}
