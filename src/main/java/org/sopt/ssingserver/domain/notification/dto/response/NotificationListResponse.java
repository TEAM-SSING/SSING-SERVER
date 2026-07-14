package org.sopt.ssingserver.domain.notification.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.sopt.ssingserver.domain.notification.enums.NotificationType;

public record NotificationListResponse(
        List<NotificationItemResponse> notifications,
        String nextCursor,
        boolean hasNext
) {

    public record NotificationItemResponse(
            Long notificationId,
            NotificationType type,
            String title,
            String body,
            Map<String, Object> payload,
            boolean isRead,
            Instant createdAt
    ) {
    }
}
