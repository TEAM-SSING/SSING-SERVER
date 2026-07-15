package org.sopt.ssingserver.domain.notification.service;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.notification.dto.response.NotificationListResponse;
import org.sopt.ssingserver.domain.notification.dto.response.NotificationListResponse.NotificationItemResponse;
import org.sopt.ssingserver.domain.notification.entity.Notification;
import org.sopt.ssingserver.domain.notification.enums.ClientApp;
import org.sopt.ssingserver.domain.notification.repository.NotificationRepository;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private static final int RETENTION_DAYS = 7;
    private static final String CURSOR_SEPARATOR = "_";
    private static final TypeReference<Map<String, Object>> DATA_JSON_TYPE = new TypeReference<>() {
    };

    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    // 현재 회원 앱 기준의 최근 알림을 커서 기반으로 조회하고 다음 페이지 여부를 계산함
    public NotificationListResponse getNotifications(
            CurrentMember currentMember,
            String cursor,
            int size
    ) {
        Cursor decodedCursor = decodeCursor(cursor);
        PageRequest pageRequest = PageRequest.of(0, size + 1);
        Instant since = clock.instant().minus(RETENTION_DAYS, ChronoUnit.DAYS);
        ClientApp clientApp = clientAppFrom(currentMember.role());

        List<Notification> queriedNotifications = decodedCursor == null
                ? notificationRepository.findFirstPage(currentMember.memberId(), clientApp, since, pageRequest)
                : notificationRepository.findNextPage(
                        currentMember.memberId(),
                        clientApp,
                        since,
                        decodedCursor.createdAt(),
                        decodedCursor.notificationId(),
                        pageRequest
                );

        boolean hasNext = queriedNotifications.size() > size;
        List<Notification> notifications = hasNext
                ? queriedNotifications.subList(0, size)
                : queriedNotifications;

        return new NotificationListResponse(
                notifications.stream()
                        .map(this::toNotificationItemResponse)
                        .toList(),
                nextCursor(notifications, hasNext),
                hasNext
        );
    }

    // 회원 역할에 따라 알림을 노출할 앱 구분값을 결정함
    private ClientApp clientAppFrom(MemberRole memberRole) {
        return switch (memberRole) {
            case CONSUMER -> ClientApp.CONSUMER;
            case INSTRUCTOR -> ClientApp.INSTRUCTOR;
            case ADMIN -> throw new BusinessException(CommonErrorCode.FORBIDDEN);
        };
    }

    // 알림 엔티티를 목록 조회 응답의 단일 알림 항목으로 변환함
    private NotificationItemResponse toNotificationItemResponse(Notification notification) {
        return new NotificationItemResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                deepLinkFrom(notification.getDataJson()),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }

    // DB에 문자열로 저장된 알림 data JSON에서 목록 응답에 필요한 딥링크를 추출함
    private String deepLinkFrom(String dataJson) {
        return requiredPayloadString(parseDataJson(dataJson), "deepLink");
    }

    private Map<String, Object> parseDataJson(String dataJson) {
        if (dataJson == null || dataJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(dataJson, DATA_JSON_TYPE);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Notification dataJson must be a JSON object.", exception);
        }
    }

    private String requiredPayloadString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            throw new IllegalStateException("Notification payload " + key + " must be a non-blank string.");
        }
        return stringValue;
    }

    // 다음 페이지가 있을 때 마지막 응답 알림을 기준으로 다음 조회 커서를 생성함
    private String nextCursor(List<Notification> notifications, boolean hasNext) {
        if (!hasNext || notifications.isEmpty()) {
            return null;
        }
        Notification lastNotification = notifications.getLast();
        return encodeCursor(new Cursor(lastNotification.getCreatedAt(), lastNotification.getId()));
    }

    // 클라이언트가 전달한 createdAt_notificationId 커서를 다음 페이지 조회 기준값으로 복원함
    private Cursor decodeCursor(String cursor) {
        if (cursor == null) {
            return null;
        }
        try {
            int separatorIndex = cursor.lastIndexOf(CURSOR_SEPARATOR);
            if (separatorIndex < 1 || separatorIndex == cursor.length() - 1) {
                throw new IllegalArgumentException("Invalid notification cursor.");
            }
            Instant createdAt = Instant.parse(cursor.substring(0, separatorIndex));
            Long notificationId = Long.parseLong(cursor.substring(separatorIndex + 1));
            return new Cursor(createdAt, notificationId);
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, exception);
        }
    }

    // 다음 페이지의 마지막 조회 기준을 createdAt_notificationId 평문 커서로 생성함
    private String encodeCursor(Cursor cursor) {
        return cursor.createdAt() + CURSOR_SEPARATOR + cursor.notificationId();
    }

    // createdAt DESC, id DESC 정렬에서 다음 페이지 경계를 표현하는 서비스 내부 cursor 값
    private record Cursor(
            Instant createdAt,
            Long notificationId
    ) {
    }
}
