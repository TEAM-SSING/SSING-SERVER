package org.sopt.ssingserver.domain.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.notification.enums.ClientApp;
import org.sopt.ssingserver.domain.notification.enums.NotificationType;

class NotificationTest {

    @Test
    void create는_알림함에_필요한_정보를_저장한다() {
        Member member = activeMember("강사", MemberRole.INSTRUCTOR);
        String dataJson = "{\"matchingOfferId\":10}";

        Notification notification = Notification.create(
                member,
                ClientApp.INSTRUCTOR,
                NotificationType.MATCHING_OFFER_RECEIVED,
                "씽 매칭 강습 도착",
                "새로운 강습이 도착했어요. 강습생 정보를 확인하고 강습을 수락해보세요.",
                dataJson
        );

        assertThat(notification.getMember()).isSameAs(member);
        assertThat(notification.getClientApp()).isSameAs(ClientApp.INSTRUCTOR);
        assertThat(notification.getType()).isSameAs(NotificationType.MATCHING_OFFER_RECEIVED);
        assertThat(notification.getTitle()).isEqualTo("씽 매칭 강습 도착");
        assertThat(notification.getBody()).isEqualTo("새로운 강습이 도착했어요. 강습생 정보를 확인하고 강습을 수락해보세요.");
        assertThat(notification.getDataJson()).isEqualTo(dataJson);
        assertThat(notification.isRead()).isFalse();
        assertThat(notification.getReadAt()).isNull();
    }

    @Test
    void markRead는_처음_읽은_시각만_저장한다() {
        Notification notification = Notification.create(
                activeMember("강사", MemberRole.INSTRUCTOR),
                ClientApp.INSTRUCTOR,
                NotificationType.MATCHING_OFFER_CLOSED,
                "강습 거절",
                "요청 받았던 강습이 거절되었어요. 다른 요청을 받아볼까요?",
                "{\"matchingOfferId\":\"10\",\"closedReason\":\"GROUP_CANCELED\"}"
        );
        Instant firstReadAt = Instant.parse("2026-07-15T10:00:00Z");
        Instant secondReadAt = Instant.parse("2026-07-15T11:00:00Z");

        notification.markRead(firstReadAt);
        notification.markRead(secondReadAt);

        assertThat(notification.isRead()).isTrue();
        assertThat(notification.getReadAt()).isEqualTo(firstReadAt);
    }

    private Member activeMember(String nickname, MemberRole role) {
        return Member.create(nickname, null, role, MemberStatus.ACTIVE);
    }
}
