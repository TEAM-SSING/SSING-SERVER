package org.sopt.ssingserver.domain.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.sopt.ssingserver.domain.notification.entity.Notification;
import org.sopt.ssingserver.domain.notification.enums.NotificationType;

@Schema(description = "최근 7일 알림의 커서 기반 목록")
public record NotificationListResponse(
        @Schema(
                description = "최신순 알림 목록",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        List<NotificationItemResponse> notifications,

        @Schema(
                description = "다음 페이지 조회용 커서. hasNext가 false이면 null입니다.",
                example = "2026-07-04T12:59:00Z_99"
        )
        String nextCursor,

        @Schema(
                description = "다음 페이지 존재 여부",
                example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        boolean hasNext
) {

    public static NotificationListResponse of(
            List<Notification> notifications,
            String nextCursor,
            boolean hasNext
    ) {
        return new NotificationListResponse(
                notifications.stream()
                        .map(NotificationItemResponse::from)
                        .toList(),
                nextCursor,
                hasNext
        );
    }

    @Schema(name = "NotificationItemResponse", description = "알림 목록의 단일 항목")
    public record NotificationItemResponse(
            @Schema(description = "알림 ID", example = "99", requiredMode = Schema.RequiredMode.REQUIRED)
            Long notificationId,

            @Schema(
                    description = "클라이언트 분기 및 분석용 알림 타입",
                    example = "MATCHING_OFFER_RECEIVED",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            NotificationType type,

            @Schema(
                    description = "알림 목록 화면에 표시할 제목",
                    example = "씽 매칭 강습 도착",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            String title,

            @Schema(
                    description = "알림 목록 화면에 표시할 본문",
                    example = "새로운 강습이 도착했어요. 강습생 정보를 확인하고 강습을 수락해보세요.",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            String body,

            @Schema(description = "읽음 여부", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
            boolean isRead,

            @Schema(
                    description = "알림 생성 시각",
                    example = "2026-07-04T12:59:00Z",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            Instant createdAt
    ) {

        public static NotificationItemResponse from(Notification notification) {
            return new NotificationItemResponse(
                    notification.getId(),
                    notification.getType(),
                    notification.getTitle(),
                    notification.getBody(),
                    notification.isRead(),
                    notification.getCreatedAt()
            );
        }
    }
}
