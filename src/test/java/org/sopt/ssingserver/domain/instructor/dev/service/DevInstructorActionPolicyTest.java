package org.sopt.ssingserver.domain.instructor.dev.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.instructor.dev.enums.DevInstructorActionKey;
import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;

class DevInstructorActionPolicyTest {

    private final DevInstructorActionPolicy policy = new DevInstructorActionPolicy();

    @Test
    void 실제_활성_소비자는_프로필이_없을_때_신청만_만들_수_있다() {
        assertThat(policy.availableActions(context(
                MemberRole.CONSUMER,
                MemberStatus.ACTIVE,
                false,
                null,
                false,
                false
        ))).containsExactly(DevInstructorActionKey.CREATE_APPLICATION);
    }

    @Test
    void 승인대기_소비자는_설정을_선택해_승인할_수_있다() {
        assertThat(policy.availableActions(context(
                MemberRole.CONSUMER,
                MemberStatus.ACTIVE,
                true,
                InstructorApprovalStatus.PENDING,
                false,
                false
        ))).containsExactly(DevInstructorActionKey.APPROVE_WITH_CONFIGURATION);
    }

    @Test
    void 승인강사는_OFF에서_설정저장과_시작을_분리해_실행한다() {
        assertThat(policy.availableActions(context(
                MemberRole.INSTRUCTOR,
                MemberStatus.ACTIVE,
                true,
                InstructorApprovalStatus.APPROVED,
                true,
                false
        ))).containsExactly(
                DevInstructorActionKey.SAVE_CONFIGURATION,
                DevInstructorActionKey.START_MATCHING
        );
    }

    @Test
    void 노출중인_승인강사는_중단만_할_수_있다() {
        assertThat(policy.availableActions(context(
                MemberRole.INSTRUCTOR,
                MemberStatus.ACTIVE,
                true,
                InstructorApprovalStatus.APPROVED,
                true,
                true
        ))).containsExactly(DevInstructorActionKey.STOP_MATCHING);
    }

    @Test
    void 정지회원이나_역할과_승인상태가_엇갈린_회원은_동작할_수_없다() {
        assertThat(policy.availableActions(context(
                MemberRole.CONSUMER,
                MemberStatus.SUSPENDED,
                false,
                null,
                false,
                false
        ))).isEmpty();
        assertThat(policy.availableActions(context(
                MemberRole.CONSUMER,
                MemberStatus.ACTIVE,
                true,
                InstructorApprovalStatus.APPROVED,
                true,
                false
        ))).isEmpty();
    }

    @Test
    void 진행중인_소비자_매칭이_있으면_역할을_바꾸는_승인을_숨긴다() {
        assertThat(policy.availableActions(context(
                MemberRole.CONSUMER,
                MemberStatus.ACTIVE,
                true,
                InstructorApprovalStatus.PENDING,
                false,
                false,
                true
        ))).isEmpty();
    }

    private DevInstructorActionContext context(
            MemberRole role,
            MemberStatus status,
            boolean hasProfile,
            InstructorApprovalStatus approvalStatus,
            boolean configurationComplete,
            boolean exposed
    ) {
        return context(
                role,
                status,
                hasProfile,
                approvalStatus,
                configurationComplete,
                exposed,
                false
        );
    }

    private DevInstructorActionContext context(
            MemberRole role,
            MemberStatus status,
            boolean hasProfile,
            InstructorApprovalStatus approvalStatus,
            boolean configurationComplete,
            boolean exposed,
            boolean consumerFlowActive
    ) {
        return new DevInstructorActionContext(
                role,
                status,
                hasProfile,
                approvalStatus,
                configurationComplete,
                exposed,
                consumerFlowActive
        );
    }
}
