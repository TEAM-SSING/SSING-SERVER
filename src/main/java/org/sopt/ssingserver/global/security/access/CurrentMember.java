package org.sopt.ssingserver.global.security.access;

import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;

public record CurrentMember(
        Long memberId,
        MemberRole role,
        MemberStatus memberStatus,
        InstructorApprovalStatus instructorApprovalStatus
) {

    public boolean isActive() {
        return memberStatus == MemberStatus.ACTIVE;
    }

    public boolean isActiveConsumer() {
        return isActive()
                && role == MemberRole.CONSUMER;
    }

    public boolean isPendingInstructor() {
        return isActive()
                && role == MemberRole.CONSUMER
                && instructorApprovalStatus == InstructorApprovalStatus.PENDING;
    }

    public boolean isApprovedInstructor() {
        return isActive()
                && role == MemberRole.INSTRUCTOR
                && instructorApprovalStatus == InstructorApprovalStatus.APPROVED;
    }

    public boolean isActiveAdmin() {
        return isActive()
                && role == MemberRole.ADMIN;
    }
}
