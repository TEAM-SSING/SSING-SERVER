package org.sopt.ssingserver.domain.instructor.dev.service;

import java.util.List;
import org.sopt.ssingserver.domain.instructor.dev.enums.DevInstructorActionKey;
import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile({"local", "dev"})
@ConditionalOnProperty(name = "ssing.dev-instructor-actions.enabled", havingValue = "true")
@Component
class DevInstructorActionPolicy {

    private static final List<MatchingRequestStatus> ROLE_CHANGE_BLOCKING_STATUSES = List.of(
            MatchingRequestStatus.REQUESTED,
            MatchingRequestStatus.GROUPED,
            MatchingRequestStatus.MATCHED,
            MatchingRequestStatus.CONFIRMED
    );

    static List<MatchingRequestStatus> roleChangeBlockingStatuses() {
        return ROLE_CHANGE_BLOCKING_STATUSES;
    }

    List<DevInstructorActionKey> availableActions(DevInstructorActionContext context) {
        if (context.memberStatus() != MemberStatus.ACTIVE) {
            return List.of();
        }
        if (!context.hasProfile() && context.memberRole() == MemberRole.CONSUMER) {
            return List.of(DevInstructorActionKey.CREATE_APPLICATION);
        }
        if (context.hasProfile()
                && context.memberRole() == MemberRole.CONSUMER
                && context.approvalStatus() == InstructorApprovalStatus.PENDING
                && !context.exposed()
                && !context.consumerFlowActive()) {
            return List.of(DevInstructorActionKey.APPROVE_WITH_CONFIGURATION);
        }
        if (context.hasProfile()
                && context.memberRole() == MemberRole.INSTRUCTOR
                && context.approvalStatus() == InstructorApprovalStatus.APPROVED) {
            if (context.exposed()) {
                return List.of(DevInstructorActionKey.STOP_MATCHING);
            }
            if (context.configurationComplete()) {
                return List.of(
                        DevInstructorActionKey.SAVE_CONFIGURATION,
                        DevInstructorActionKey.START_MATCHING
                );
            }
            return List.of(DevInstructorActionKey.SAVE_CONFIGURATION);
        }
        return List.of();
    }
}
