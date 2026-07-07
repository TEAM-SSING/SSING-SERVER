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
    },

    APPROVED_INSTRUCTOR {
        @Override
        boolean isSatisfiedBy(CurrentMember currentMember) {
            return currentMember.isApprovedInstructor();
        }
    },

    ADMIN {
        @Override
        boolean isSatisfiedBy(CurrentMember currentMember) {
            return currentMember.isActiveAdmin();
        }
    };

    abstract boolean isSatisfiedBy(CurrentMember currentMember);
}
