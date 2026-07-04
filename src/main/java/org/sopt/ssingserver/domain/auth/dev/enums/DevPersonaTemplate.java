package org.sopt.ssingserver.domain.auth.dev.enums;

import org.sopt.ssingserver.domain.auth.dev.error.DevAuthErrorCode;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.global.error.BusinessException;

public enum DevPersonaTemplate {

    GENERAL_CONSUMER(MemberRole.CONSUMER, MemberStatus.ACTIVE, DevInstructorProfilePlan.NONE),
    SUSPENDED_CONSUMER(MemberRole.CONSUMER, MemberStatus.SUSPENDED, DevInstructorProfilePlan.NONE),
    INSTRUCTOR_PENDING(MemberRole.CONSUMER, MemberStatus.ACTIVE, DevInstructorProfilePlan.PENDING),
    INSTRUCTOR_APPROVED(MemberRole.INSTRUCTOR, MemberStatus.ACTIVE, DevInstructorProfilePlan.APPROVED);

    private final MemberRole memberRole;
    private final MemberStatus memberStatus;
    private final DevInstructorProfilePlan instructorProfilePlan;

    DevPersonaTemplate(
            MemberRole memberRole,
            MemberStatus memberStatus,
            DevInstructorProfilePlan instructorProfilePlan
    ) {
        this.memberRole = memberRole;
        this.memberStatus = memberStatus;
        this.instructorProfilePlan = instructorProfilePlan;
    }

    public static DevPersonaTemplate from(String value) {
        try {
            return DevPersonaTemplate.valueOf(value.trim());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(DevAuthErrorCode.DEV_PERSONA_INVALID_TEMPLATE, exception);
        }
    }

    public MemberRole memberRole() {
        return memberRole;
    }

    public MemberStatus memberStatus() {
        return memberStatus;
    }

    public DevInstructorProfilePlan instructorProfilePlan() {
        return instructorProfilePlan;
    }
}
