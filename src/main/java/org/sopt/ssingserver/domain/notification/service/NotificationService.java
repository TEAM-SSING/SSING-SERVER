package org.sopt.ssingserver.domain.notification.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
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
        if (memberRole == MemberRole.INSTRUCTOR) {
            return ClientApp.INSTRUCTOR;
        }
        return ClientApp.CONSUMER;
    }

    // 알림 엔티티를 목록 조회 응답의 단일 알림 항목으로 변환함
    private NotificationItemResponse toNotificationItemResponse(Notification notification) {
        return new NotificationItemResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                parseDataJson(notification.getDataJson()),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }

    // DB에 문자열로 저장된 알림 payload JSON을 응답용 객체로 변환함
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

    // 다음 페이지가 있을 때 마지막 응답 알림을 기준으로 다음 조회 커서를 생성함
    private String nextCursor(List<Notification> notifications, boolean hasNext) {
        if (!hasNext || notifications.isEmpty()) {
            return null;
        }
        Notification lastNotification = notifications.getLast();
        return encodeCursor(new Cursor(lastNotification.getCreatedAt(), lastNotification.getId()));
    }

    // 클라이언트가 전달한 opaque cursor를 createdAt, notificationId 기준값으로 복원함
    private Cursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decodedCursor = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            return objectMapper.readValue(decodedCursor, Cursor.class);
        } catch (IllegalArgumentException | JacksonException exception) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, exception);
        }
    }

    // 커서 내부 구조를 클라이언트가 해석하지 못하도록 URL-safe Base64 문자열로 인코딩함
    private String encodeCursor(Cursor cursor) {
        try {
            String json = objectMapper.writeValueAsString(cursor);
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Notification cursor must be serializable.", exception);
        }
    }

    // createdAt DESC, id DESC 정렬에서 다음 페이지 경계를 표현하는 서비스 내부 cursor 값
    private record Cursor(
            Instant createdAt,
            Long notificationId
    ) {
    }
}
