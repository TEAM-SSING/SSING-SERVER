package org.sopt.ssingserver.domain.auth.dev.enums;

import java.util.Optional;
import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;

public enum DevInstructorProfilePlan {

    NONE(null),
    PENDING(InstructorApprovalStatus.PENDING),
    APPROVED(InstructorApprovalStatus.APPROVED);

    private final InstructorApprovalStatus approvalStatus;

    DevInstructorProfilePlan(InstructorApprovalStatus approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public Optional<InstructorApprovalStatus> approvalStatus() {
        return Optional.ofNullable(approvalStatus);
    }
}
