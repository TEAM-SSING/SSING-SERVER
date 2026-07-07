package org.sopt.ssingserver.global.security.access;

import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;

public enum AccessPolicy {

    ACTIVE_MEMBER(false) {
        @Override
        boolean isSatisfiedBy(CurrentMember currentMember) {
            return isActive(currentMember);
        }
    },

    CONSUMER(false) {
        @Override
        boolean isSatisfiedBy(CurrentMember currentMember) {
            return isActive(currentMember)
                    && currentMember.role() == MemberRole.CONSUMER;
        }
    },

    PENDING_INSTRUCTOR(true) {
        @Override
        boolean isSatisfiedBy(CurrentMember currentMember) {
            return isActive(currentMember)
                    && currentMember.role() == MemberRole.CONSUMER
                    && currentMember.instructorApprovalStatus() == InstructorApprovalStatus.PENDING;
        }
    },

    APPROVED_INSTRUCTOR(true) {
        @Override
        boolean isSatisfiedBy(CurrentMember currentMember) {
            return isActive(currentMember)
                    && currentMember.role() == MemberRole.INSTRUCTOR
                    && currentMember.instructorApprovalStatus() == InstructorApprovalStatus.APPROVED;
        }
    },

    ADMIN(false) {
        @Override
        boolean isSatisfiedBy(CurrentMember currentMember) {
            return isActive(currentMember)
                    && currentMember.role() == MemberRole.ADMIN;
        }
    };

    private final boolean requiresInstructorApprovalStatus;

    AccessPolicy(boolean requiresInstructorApprovalStatus) {
        this.requiresInstructorApprovalStatus = requiresInstructorApprovalStatus;
    }

    abstract boolean isSatisfiedBy(CurrentMember currentMember);

    boolean requiresInstructorApprovalStatus() {
        return requiresInstructorApprovalStatus;
    }

    private static boolean isActive(CurrentMember currentMember) {
        return currentMember.memberStatus() == MemberStatus.ACTIVE;
    }
}
