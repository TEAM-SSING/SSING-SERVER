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
}
