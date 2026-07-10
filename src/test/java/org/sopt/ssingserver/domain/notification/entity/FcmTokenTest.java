package org.sopt.ssingserver.domain.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.notification.enums.ClientApp;
import org.sopt.ssingserver.domain.notification.enums.ClientPlatform;

class FcmTokenTest {

    @Test
    void create는_클라이언트정보와_등록시각을_저장한다() {
        Member member = activeMember("소비자", MemberRole.CONSUMER);
        Instant registeredAt = Instant.parse("2026-07-10T10:00:00Z");

        FcmToken fcmToken = FcmToken.create(
                member,
                ClientApp.CONSUMER,
                ClientPlatform.ANDROID,
                "consumer-fcm-token",
                registeredAt
        );

        assertThat(fcmToken.getMember()).isSameAs(member);
        assertThat(fcmToken.getClientApp()).isSameAs(ClientApp.CONSUMER);
        assertThat(fcmToken.getPlatform()).isSameAs(ClientPlatform.ANDROID);
        assertThat(fcmToken.getToken()).isEqualTo("consumer-fcm-token");
        assertThat(fcmToken.getLastRegisteredAt()).isEqualTo(registeredAt);
    }

    @Test
    void updateRegistration은_토큰값을_유지하고_현재_등록정보만_갱신한다() {
        FcmToken fcmToken = FcmToken.create(
                activeMember("기존회원", MemberRole.CONSUMER),
                ClientApp.CONSUMER,
                ClientPlatform.ANDROID,
                "same-fcm-token",
                Instant.parse("2026-07-10T10:00:00Z")
        );
        Member instructor = activeMember("강사회원", MemberRole.INSTRUCTOR);
        Instant registeredAt = Instant.parse("2026-07-10T11:00:00Z");

        fcmToken.updateRegistration(
                instructor,
                ClientApp.INSTRUCTOR,
                ClientPlatform.ANDROID,
                registeredAt
        );

        assertThat(fcmToken.getMember()).isSameAs(instructor);
        assertThat(fcmToken.getClientApp()).isSameAs(ClientApp.INSTRUCTOR);
        assertThat(fcmToken.getPlatform()).isSameAs(ClientPlatform.ANDROID);
        assertThat(fcmToken.getToken()).isEqualTo("same-fcm-token");
        assertThat(fcmToken.getLastRegisteredAt()).isEqualTo(registeredAt);
    }

    private Member activeMember(String nickname, MemberRole role) {
        return Member.create(nickname, null, role, MemberStatus.ACTIVE);
    }
}
