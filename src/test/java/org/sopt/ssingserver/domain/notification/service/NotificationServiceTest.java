package org.sopt.ssingserver.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.notification.dto.response.NotificationListResponse;
import org.sopt.ssingserver.domain.notification.entity.Notification;
import org.sopt.ssingserver.domain.notification.enums.ClientApp;
import org.sopt.ssingserver.domain.notification.enums.NotificationType;
import org.sopt.ssingserver.domain.notification.repository.NotificationRepository;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-07-15T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Instant SINCE = Instant.parse("2026-07-08T00:00:00Z");

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository,
                new ObjectMapper(),
                FIXED_CLOCK
        );
    }

    @Test
    void getNotifications는_size보다_하나_더_조회해서_nextCursor를_생성한다() {
        Notification first = notification(100L, Instant.parse("2026-07-14T10:00:00Z"), null);
        Notification second = notification(99L, Instant.parse("2026-07-14T09:00:00Z"), null);
        Notification extra = notification(98L, Instant.parse("2026-07-14T08:00:00Z"), null);
        when(notificationRepository.findFirstPage(
                MEMBER_ID,
                ClientApp.INSTRUCTOR,
                SINCE,
                PageRequest.of(0, 3)
        )).thenReturn(List.of(first, second, extra));

        NotificationListResponse response = notificationService.getNotifications(
                currentMember(MemberRole.INSTRUCTOR),
                null,
                2
        );

        assertThat(response.notifications()).hasSize(2);
        assertThat(response.notifications().get(0).notificationId()).isEqualTo(100L);
        assertThat(response.notifications().get(0).deepLink())
                .isEqualTo("ssing://matching/offers/10");
        assertThat(response.nextCursor()).isEqualTo("2026-07-14T09:00:00Z_99");
        assertThat(response.hasNext()).isTrue();
    }

    @Test
    void getNotifications는_nextCursor를_다음_페이지_조건으로_해석한다() {
        Notification firstPageLast = notification(99L, Instant.parse("2026-07-14T09:00:00Z"), null);
        when(notificationRepository.findFirstPage(
                MEMBER_ID,
                ClientApp.INSTRUCTOR,
                SINCE,
                PageRequest.of(0, 2)
        )).thenReturn(List.of(firstPageLast, notification(98L, Instant.parse("2026-07-14T08:00:00Z"), null)));
        String nextCursor = notificationService.getNotifications(
                currentMember(MemberRole.INSTRUCTOR),
                null,
                1
        ).nextCursor();
        when(notificationRepository.findNextPage(
                MEMBER_ID,
                ClientApp.INSTRUCTOR,
                SINCE,
                Instant.parse("2026-07-14T09:00:00Z"),
                99L,
                PageRequest.of(0, 2)
        )).thenReturn(List.of(notification(97L, Instant.parse("2026-07-14T07:00:00Z"), null)));

        notificationService.getNotifications(currentMember(MemberRole.INSTRUCTOR), nextCursor, 1);

        verify(notificationRepository).findNextPage(
                MEMBER_ID,
                ClientApp.INSTRUCTOR,
                SINCE,
                Instant.parse("2026-07-14T09:00:00Z"),
                99L,
                PageRequest.of(0, 2)
        );
    }

    @Test
    void getNotifications는_마지막_페이지면_nextCursor는_null이고_hasNext는_false를_반환한다() {
        when(notificationRepository.findFirstPage(
                MEMBER_ID,
                ClientApp.CONSUMER,
                SINCE,
                PageRequest.of(0, 21)
        )).thenReturn(List.of(notification(100L, Instant.parse("2026-07-14T10:00:00Z"), null)));

        NotificationListResponse response = notificationService.getNotifications(
                currentMember(MemberRole.CONSUMER),
                null,
                20
        );

        assertThat(response.notifications()).hasSize(1);
        assertThat(response.nextCursor()).isNull();
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void getNotifications는_잘못된_cursor면_BAD_REQUEST를_던진다() {
        assertThatThrownBy(() -> notificationService.getNotifications(
                currentMember(MemberRole.CONSUMER),
                "not-a-cursor",
                20
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
    }

    @Test
    void getNotifications는_빈_cursor면_BAD_REQUEST를_던진다() {
        assertThatThrownBy(() -> notificationService.getNotifications(
                currentMember(MemberRole.CONSUMER),
                " ",
                20
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
    }

    @Test
    void getNotifications는_ADMIN을_소비자_앱으로_분류하지_않는다() {
        assertThatThrownBy(() -> notificationService.getNotifications(
                currentMember(MemberRole.ADMIN),
                null,
                20
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommonErrorCode.FORBIDDEN);
    }

    private Notification notification(Long id, Instant createdAt, Instant readAt) {
        Notification notification = Notification.create(
                Member.create("회원", null, MemberRole.INSTRUCTOR, MemberStatus.ACTIVE),
                ClientApp.INSTRUCTOR,
                NotificationType.MATCHING_OFFER_RECEIVED,
                "씽 매칭 강습 도착",
                "새로운 강습이 도착했어요. 강습생 정보를 확인하고 강습을 수락해보세요.",
                "{\"deepLink\":\"ssing://matching/offers/10\",\"matchingOfferId\":\"10\"}"
        );
        ReflectionTestUtils.setField(notification, "id", id);
        ReflectionTestUtils.setField(notification, "createdAt", createdAt);
        ReflectionTestUtils.setField(notification, "readAt", readAt);
        return notification;
    }

    private CurrentMember currentMember(MemberRole role) {
        return new CurrentMember(MEMBER_ID, role, MemberStatus.ACTIVE, null);
    }
}
