package org.sopt.ssingserver.domain.instructor.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.instructor.enums.InstructorCertificateType;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.Gender;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.springframework.test.util.ReflectionTestUtils;

class InstructorProfileTest {

    @Test
    void 여러_자격증의_종목을_중복_제거해_반환한다() {
        InstructorProfile profile = instructorProfile();

        profile.registerCertificate(InstructorCertificateType.KSIA_SKI_LEVEL_1);
        profile.registerCertificate(InstructorCertificateType.KSIA_SKI_LEVEL_2);
        profile.registerCertificate(InstructorCertificateType.SBAK_SNOWBOARD_TEACHING_3);

        assertThat(profile.getCertificateTypes()).hasSize(3);
        assertThat(profile.getAvailableSports()).containsExactly(Sport.SKI, Sport.SNOWBOARD);
        assertThat(profile.hasCertificateFor(Sport.SKI)).isTrue();
        assertThat(profile.hasCertificateFor(Sport.SNOWBOARD)).isTrue();
    }

    @Test
    void 기존_단일_자격증_컬럼도_선택_가능_종목에_포함한다() {
        InstructorProfile profile = instructorProfile();
        ReflectionTestUtils.setField(
                profile,
                "certificateType",
                InstructorCertificateType.KSIA_SNOWBOARD_LEVEL_1
        );

        assertThat(profile.getCertificateTypes())
                .containsExactly(InstructorCertificateType.KSIA_SNOWBOARD_LEVEL_1);
        assertThat(profile.getAvailableSports()).containsExactly(Sport.SNOWBOARD);
    }

    @Test
    void 자격증이_없으면_선택_가능한_종목도_없다() {
        InstructorProfile profile = instructorProfile();

        assertThat(profile.getCertificateTypes()).isEmpty();
        assertThat(profile.getAvailableSports()).isEmpty();
        assertThat(profile.hasCertificateFor(null)).isFalse();
    }

    @Test
    void 외부에서_자격증_목록을_직접_변경할_수_없다() {
        InstructorProfile profile = instructorProfile();
        profile.registerCertificate(InstructorCertificateType.KSIA_SKI_LEVEL_1);

        assertThatThrownBy(() -> profile.getCertificateTypes().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> profile.registerCertificate(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void approve는_PENDING_프로필에_리조트와_승인시각을_함께_저장한다() {
        InstructorProfile profile = pendingInstructorProfile();
        Resort resort = Resort.create("VIVALDI", "비발디파크", "비발디파크", 10_000);
        Instant approvedAt = Instant.parse("2026-07-17T01:00:00Z");

        profile.approve(resort, approvedAt);

        assertThat(profile.getApprovalStatus()).isSameAs(InstructorApprovalStatus.APPROVED);
        assertThat(profile.getApprovedAt()).isEqualTo(approvedAt);
        assertThat(profile.getResort()).isSameAs(resort);
    }

    @Test
    void changeResort는_승인된_프로필의_활동_리조트를_바꾼다() {
        InstructorProfile profile = instructorProfile();
        Resort resort = Resort.create("YONGPYONG", "용평리조트", "모나 용평", 12_000);

        profile.changeResort(resort);

        assertThat(profile.getResort()).isSameAs(resort);
    }

    @Test
    void approve는_PENDING이_아니면_기존_승인상태를_바꾸지_않는다() {
        InstructorProfile profile = instructorProfile();
        Resort resort = Resort.create("VIVALDI", "비발디파크", "비발디파크", 10_000);

        assertThatThrownBy(() -> profile.approve(
                resort,
                Instant.parse("2026-07-17T01:00:00Z")
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only pending instructor profiles can be approved.");
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
                Instant.parse("2026-07-07T00:00:00Z")
        );
    }

    private InstructorProfile pendingInstructorProfile() {
        Member member = Member.createOAuthMember("승인대기강사", null);
        return InstructorProfile.create(
                member,
                "승인대기강사",
                "010-0000-0000",
                Gender.MALE,
                LocalDate.of(2000, 1, 1),
                "테스트 강사 프로필",
                LocalDate.of(2020, 1, 1),
                InstructorApprovalStatus.PENDING,
                null
        );
    }
}
