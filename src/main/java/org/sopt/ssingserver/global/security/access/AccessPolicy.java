package org.sopt.ssingserver.global.security.access;

public enum AccessPolicy {

    ACTIVE_MEMBER {
        @Override
        boolean isSatisfiedBy(CurrentMember currentMember) {
            return currentMember.isActive();
        }
    },

    CONSUMER {
        @Override
        boolean isSatisfiedBy(CurrentMember currentMember) {
            return currentMember.isActiveConsumer();
        }
    },

    PENDING_INSTRUCTOR {
        @Override
        boolean isSatisfiedBy(CurrentMember currentMember) {
            return currentMember.isPendingInstructor();
        }

        @Override
        boolean requiresInstructorApprovalStatus() {
            return true;
        }
    },

    APPROVED_INSTRUCTOR {
        @Override
        boolean isSatisfiedBy(CurrentMember currentMember) {
            return currentMember.isApprovedInstructor();
        }

        @Override
        boolean requiresInstructorApprovalStatus() {
            return true;
        }
    },

    ADMIN {
        @Override
        boolean isSatisfiedBy(CurrentMember currentMember) {
            return currentMember.isActiveAdmin();
        }
    };

    abstract boolean isSatisfiedBy(CurrentMember currentMember);

    boolean requiresInstructorApprovalStatus() {
        return false;
    }
}
