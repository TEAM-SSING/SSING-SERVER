package org.sopt.ssingserver.global.security.access;

import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;

public enum AccessPolicy {

    ACTIVE_MEMBER {
        @Override
        boolean isSatisfiedBy(CurrentMember currentMember) {
            return isActive(currentMember);
        }
    },

    CONSUMER {
        @Override
        boolean isSatisfiedBy(CurrentMember currentMember) {
            return isActive(currentMember)
                    && currentMember.role() == MemberRole.CONSUMER;
        }
    },

    PENDING_INSTRUCTOR {
        @Override
        boolean isSatisfiedBy(CurrentMember currentMember) {
            return isActive(currentMember)
                    && currentMember.role() == MemberRole.CONSUMER
                    && currentMember.instructorApprovalStatus() == InstructorApprovalStatus.PENDING;
        }

        @Override
        boolean requiresInstructorApprovalStatus() {
            return true;
        }
    },

    APPROVED_INSTRUCTOR {
        @Override
        boolean isSatisfiedBy(CurrentMember currentMember) {
            return isActive(currentMember)
                    && currentMember.role() == MemberRole.INSTRUCTOR
                    && currentMember.instructorApprovalStatus() == InstructorApprovalStatus.APPROVED;
        }

        @Override
        boolean requiresInstructorApprovalStatus() {
            return true;
        }
    },

    ADMIN {
        @Override
        boolean isSatisfiedBy(CurrentMember currentMember) {
            return isActive(currentMember)
                    && currentMember.role() == MemberRole.ADMIN;
        }
    };

    abstract boolean isSatisfiedBy(CurrentMember currentMember);

    boolean requiresInstructorApprovalStatus() {
        return false;
    }

    private static boolean isActive(CurrentMember currentMember) {
        return currentMember.memberStatus() == MemberStatus.ACTIVE;
    }
}
