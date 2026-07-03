package org.sopt.ssingserver.domain.auth.dev.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.auth.dev.error.DevAuthErrorCode;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.global.error.BusinessException;

class DevPersonaTemplateTest {

    @Test
    void from은_허용된_템플릿_이름만_개발용_persona_template으로_변환한다() {
        DevPersonaTemplate template = DevPersonaTemplate.from("INSTRUCTOR_APPROVED");

        assertThat(template.memberRole()).isEqualTo(MemberRole.INSTRUCTOR);
        assertThat(template.memberStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(template.instructorProfilePlan()).isEqualTo(DevInstructorProfilePlan.APPROVED);
    }

    @Test
    void from은_ADMIN이나_ON_DEMAND같은_확정하지_않은_템플릿을_거부한다() {
        // 개발 도구가 관리자 계정이나 자동 생성 모드를 몰래 만들지 못하게 막는 정책 테스트다.
        assertInvalidTemplate("ADMIN");
        assertInvalidTemplate("ON_DEMAND");
        assertInvalidTemplate(null);
    }

    private void assertInvalidTemplate(String value) {
        assertThatThrownBy(() -> DevPersonaTemplate.from(value))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isSameAs(DevAuthErrorCode.DEV_PERSONA_INVALID_TEMPLATE));
    }
}
