package org.sopt.ssingserver.domain.auth.dto.response;

import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;

public enum InstructorStatusResponse {
    NONE,
    PENDING,
    APPROVED,
    REJECTED,
    SUSPENDED;

    public static InstructorStatusResponse from(InstructorApprovalStatus approvalStatus) {
        return switch (approvalStatus) {
            case PENDING -> PENDING;
            case APPROVED -> APPROVED;
            case REJECTED -> REJECTED;
            case SUSPENDED -> SUSPENDED;
        };
    }
}
