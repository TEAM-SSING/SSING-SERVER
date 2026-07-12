package org.sopt.ssingserver.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;

@Schema(
        name = "InstructorStatus",
        description = "강사 프로필 승인 상태",
        allowableValues = {"NONE", "PENDING", "APPROVED", "REJECTED", "SUSPENDED"}
)
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
