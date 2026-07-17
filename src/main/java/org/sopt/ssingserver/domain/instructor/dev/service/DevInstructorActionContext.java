package org.sopt.ssingserver.domain.instructor.dev.service;

import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;

record DevInstructorActionContext(
        MemberRole memberRole,
        MemberStatus memberStatus,
        boolean hasProfile,
        InstructorApprovalStatus approvalStatus,
        boolean configurationComplete,
        boolean exposed,
        boolean consumerFlowActive
) {
}
