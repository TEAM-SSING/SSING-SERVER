package org.sopt.ssingserver.global.security.access;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;

class CurrentMemberTest {

    @Test
    void isActive는_ACTIVE_상태만_true를_반환한다() {
        assertThat(currentMember(MemberRole.CONSUMER, MemberStatus.ACTIVE, null).isActive()).isTrue();
        assertThat(currentMember(MemberRole.CONSUMER, MemberStatus.SUSPENDED, null).isActive()).isFalse();
        assertThat(currentMember(MemberRole.CONSUMER, MemberStatus.WITHDRAWN, null).isActive()).isFalse();
    }

    @Test
    void isActiveConsumer는_ACTIVE_CONSUMER만_true를_반환한다() {
        assertThat(currentMember(MemberRole.CONSUMER, MemberStatus.ACTIVE, null).isActiveConsumer()).isTrue();
        assertThat(currentMember(MemberRole.CONSUMER, MemberStatus.SUSPENDED, null).isActiveConsumer()).isFalse();
        assertThat(currentMember(MemberRole.INSTRUCTOR, MemberStatus.ACTIVE, null).isActiveConsumer()).isFalse();
    }

    @Test
    void isPendingInstructor는_ACTIVE_CONSUMER와_PENDING_강사상태만_true를_반환한다() {
        assertThat(currentMember(
                MemberRole.CONSUMER,
                MemberStatus.ACTIVE,
                InstructorApprovalStatus.PENDING
        ).isPendingInstructor()).isTrue();
        assertThat(currentMember(MemberRole.CONSUMER, MemberStatus.ACTIVE, null).isPendingInstructor()).isFalse();
        assertThat(currentMember(
                MemberRole.INSTRUCTOR,
                MemberStatus.ACTIVE,
                InstructorApprovalStatus.PENDING
        ).isPendingInstructor()).isFalse();
        assertThat(currentMember(
                MemberRole.CONSUMER,
                MemberStatus.SUSPENDED,
                InstructorApprovalStatus.PENDING
        ).isPendingInstructor()).isFalse();
    }

    @Test
    void isApprovedInstructor는_ACTIVE_INSTRUCTOR와_APPROVED_강사상태만_true를_반환한다() {
        assertThat(currentMember(
                MemberRole.INSTRUCTOR,
                MemberStatus.ACTIVE,
                InstructorApprovalStatus.APPROVED
        ).isApprovedInstructor()).isTrue();
        assertThat(currentMember(
                MemberRole.INSTRUCTOR,
                MemberStatus.ACTIVE,
                InstructorApprovalStatus.PENDING
        ).isApprovedInstructor()).isFalse();
        assertThat(currentMember(
                MemberRole.CONSUMER,
                MemberStatus.ACTIVE,
                InstructorApprovalStatus.APPROVED
        ).isApprovedInstructor()).isFalse();
        assertThat(currentMember(
                MemberRole.INSTRUCTOR,
                MemberStatus.SUSPENDED,
                InstructorApprovalStatus.APPROVED
        ).isApprovedInstructor()).isFalse();
    }

    @Test
    void isActiveAdmin은_ACTIVE_ADMIN만_true를_반환한다() {
        assertThat(currentMember(MemberRole.ADMIN, MemberStatus.ACTIVE, null).isActiveAdmin()).isTrue();
        assertThat(currentMember(MemberRole.ADMIN, MemberStatus.SUSPENDED, null).isActiveAdmin()).isFalse();
        assertThat(currentMember(MemberRole.CONSUMER, MemberStatus.ACTIVE, null).isActiveAdmin()).isFalse();
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
