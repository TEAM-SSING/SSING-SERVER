package org.sopt.ssingserver.domain.instructor.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.Gender;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;

class InstructorPricePolicyTest {

    @Test
    void createActive는_가격을_활성_정책으로_만든다() {
        InstructorPricePolicy policy = InstructorPricePolicy.createActive(
                instructorProfile(),
                100_000,
                20_000
        );

        assertThat(policy.getBasePriceAmount()).isEqualTo(100_000);
        assertThat(policy.getAdditionalPersonPriceAmount()).isEqualTo(20_000);
        assertThat(policy.isActive()).isTrue();
    }

    @Test
    void deactivate는_기존_가격정책을_비활성화한다() {
        InstructorPricePolicy policy = InstructorPricePolicy.createActive(
                instructorProfile(),
                100_000,
                20_000
        );

        policy.deactivate();

        assertThat(policy.isActive()).isFalse();
    }

    @Test
    void createActive는_음수_가격을_허용하지_않는다() {
        assertThatThrownBy(() -> InstructorPricePolicy.createActive(
                instructorProfile(),
                -1,
                0
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Price amounts must be non-negative.");
    }

    private InstructorProfile instructorProfile() {
        Member member = Member.create(
                "승인강사",
                null,
                MemberRole.INSTRUCTOR,
                MemberStatus.ACTIVE
        );
        return InstructorProfile.create(
                member,
                "승인강사",
                "010-0000-0000",
                Gender.MALE,
                LocalDate.of(2000, 1, 1),
                "테스트 강사 프로필",
                LocalDate.of(2020, 1, 1),
                InstructorApprovalStatus.APPROVED,
                Instant.parse("2026-07-17T00:00:00Z")
        );
    }
}
