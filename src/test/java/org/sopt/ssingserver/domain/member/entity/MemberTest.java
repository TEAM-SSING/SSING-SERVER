package org.sopt.ssingserver.domain.member.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;

class MemberTest {

    @Test
    void createOAuthMember는_승인전_기본권한과_활성상태로_생성한다() {
        Member member = Member.createOAuthMember("스키초보", "https://image.example/profile.png");

        assertThat(member.getNickname()).isEqualTo("스키초보");
        assertThat(member.getProfileImageUrl()).isEqualTo("https://image.example/profile.png");
        assertThat(member.getRole()).isSameAs(MemberRole.CONSUMER);
        assertThat(member.getStatus()).isSameAs(MemberStatus.ACTIVE);
    }

    @Test
    void create는_지정한_회원권한과_상태를_그대로_저장한다() {
        Member member = Member.create(
                "정지강사",
                null,
                MemberRole.INSTRUCTOR,
                MemberStatus.SUSPENDED
        );

        assertThat(member.getNickname()).isEqualTo("정지강사");
        assertThat(member.getProfileImageUrl()).isNull();
        assertThat(member.getRole()).isSameAs(MemberRole.INSTRUCTOR);
        assertThat(member.getStatus()).isSameAs(MemberStatus.SUSPENDED);
    }

    @Test
    void promoteToInstructor는_활성_소비자만_강사로_승격한다() {
        Member member = Member.createOAuthMember("승인대기강사", null);

        member.promoteToInstructor();

        assertThat(member.getRole()).isSameAs(MemberRole.INSTRUCTOR);
        assertThat(member.getStatus()).isSameAs(MemberStatus.ACTIVE);
    }

    @Test
    void promoteToInstructor는_비활성_회원의_권한을_바꾸지_않는다() {
        Member member = Member.create(
                "정지회원",
                null,
                MemberRole.CONSUMER,
                MemberStatus.SUSPENDED
        );

        assertThatThrownBy(member::promoteToInstructor)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only active consumers can be promoted to instructor.");
        assertThat(member.getRole()).isSameAs(MemberRole.CONSUMER);
    }
}
