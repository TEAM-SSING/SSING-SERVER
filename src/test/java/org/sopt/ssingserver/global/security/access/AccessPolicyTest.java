package org.sopt.ssingserver.global.security.access;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;

class AccessPolicyTest {

    @Test
    void ACTIVE_MEMBER는_ACTIVE_회원이면_role과_무관하게_허용한다() {
        CurrentMember currentMember = currentMember(MemberRole.INSTRUCTOR, MemberStatus.ACTIVE, null);

        assertThat(AccessPolicy.ACTIVE_MEMBER.isSatisfiedBy(currentMember)).isTrue();
    }

    @Test
    void ACTIVE_MEMBER는_정지_회원이면_거부한다() {
        CurrentMember currentMember = currentMember(MemberRole.CONSUMER, MemberStatus.SUSPENDED, null);

        assertThat(AccessPolicy.ACTIVE_MEMBER.isSatisfiedBy(currentMember)).isFalse();
    }

    @Test
    void CONSUMER는_ACTIVE_CONSUMER만_허용한다() {
        CurrentMember currentMember = currentMember(MemberRole.CONSUMER, MemberStatus.ACTIVE, null);

        assertThat(AccessPolicy.CONSUMER.isSatisfiedBy(currentMember)).isTrue();
    }

    @Test
    void PENDING_INSTRUCTOR는_ACTIVE_CONSUMER와_PENDING_강사상태만_허용한다() {
        CurrentMember currentMember = currentMember(
                MemberRole.CONSUMER,
                MemberStatus.ACTIVE,
                InstructorApprovalStatus.PENDING
        );

        assertThat(AccessPolicy.PENDING_INSTRUCTOR.isSatisfiedBy(currentMember)).isTrue();
    }

    @Test
    void APPROVED_INSTRUCTOR는_ACTIVE_INSTRUCTOR와_APPROVED_강사상태만_허용한다() {
        CurrentMember currentMember = currentMember(
                MemberRole.INSTRUCTOR,
                MemberStatus.ACTIVE,
                InstructorApprovalStatus.APPROVED
        );

        assertThat(AccessPolicy.APPROVED_INSTRUCTOR.isSatisfiedBy(currentMember)).isTrue();
    }

    @Test
    void ADMIN은_ACTIVE_ADMIN만_허용한다() {
        CurrentMember currentMember = currentMember(MemberRole.ADMIN, MemberStatus.ACTIVE, null);

        assertThat(AccessPolicy.ADMIN.isSatisfiedBy(currentMember)).isTrue();
    }

    private CurrentMember currentMember(
            MemberRole role,
            MemberStatus memberStatus,
            InstructorApprovalStatus instructorApprovalStatus
    ) {
        return new CurrentMember(
                1L,
                role,
                memberStatus,
                instructorApprovalStatus
        );
    }
}
