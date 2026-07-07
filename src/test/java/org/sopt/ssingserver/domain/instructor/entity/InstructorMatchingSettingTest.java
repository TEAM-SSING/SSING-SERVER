package org.sopt.ssingserver.domain.instructor.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.Gender;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;

class InstructorMatchingSettingTest {

    @Test
    void create는_lessonLevels_선택목록을_저장하고_노출을_시작한다() {
        InstructorMatchingSetting setting = InstructorMatchingSetting.create(
                instructorProfile(),
                Sport.SNOWBOARD,
                List.of(LessonLevel.FIRST_TIME, LessonLevel.BEGINNER),
                List.of(120, 180, 240),
                3,
                true
        );

        assertThat(setting.getLessonLevels())
                .containsExactlyInAnyOrder(LessonLevel.FIRST_TIME, LessonLevel.BEGINNER);
        assertThat(setting.supportsLessonLevel(LessonLevel.FIRST_TIME)).isTrue();
        assertThat(setting.supportsLessonLevel(LessonLevel.CERTIFIED)).isFalse();
        assertThat(setting.getSport()).isSameAs(Sport.SNOWBOARD);
        assertThat(setting.getAvailableDurationMinutes()).containsExactlyInAnyOrder(120, 180, 240);
        assertThat(setting.supportsDurationMinutes(120)).isTrue();
        assertThat(setting.supportsDurationMinutes(60)).isFalse();
        assertThat(setting.getMaxHeadcount()).isEqualTo(3);
        assertThat(setting.isEquipmentReady()).isTrue();
        assertThat(setting.isExposed()).isTrue();
    }

    @Test
    void updateConditions는_레벨목록을_덮어쓰고_노출을_다시_시작한다() {
        InstructorMatchingSetting setting = InstructorMatchingSetting.create(
                instructorProfile(),
                Sport.SNOWBOARD,
                List.of(LessonLevel.FIRST_TIME, LessonLevel.BEGINNER),
                List.of(120, 180),
                3,
                true
        );
        setting.stopExposure();

        setting.updateConditions(
                Sport.SKI,
                List.of(LessonLevel.INTERMEDIATE, LessonLevel.CERTIFIED),
                List.of(240),
                5,
                false
        );

        assertThat(setting.getLessonLevels())
                .containsExactlyInAnyOrder(LessonLevel.INTERMEDIATE, LessonLevel.CERTIFIED);
        assertThat(setting.supportsLessonLevel(LessonLevel.BEGINNER)).isFalse();
        assertThat(setting.supportsLessonLevel(LessonLevel.CERTIFIED)).isTrue();
        assertThat(setting.getSport()).isSameAs(Sport.SKI);
        assertThat(setting.getAvailableDurationMinutes()).containsExactly(240);
        assertThat(setting.supportsDurationMinutes(180)).isFalse();
        assertThat(setting.getMaxHeadcount()).isEqualTo(5);
        assertThat(setting.isEquipmentReady()).isFalse();
        assertThat(setting.isExposed()).isTrue();
    }

    @Test
    void create는_lessonLevels가_비어있으면_생성하지_않는다() {
        assertThatThrownBy(() -> InstructorMatchingSetting.create(
                instructorProfile(),
                Sport.SNOWBOARD,
                List.of(),
                List.of(120),
                3,
                true
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("lessonLevels must not be empty.");
    }

    @Test
    void create는_lessonLevels가_null이면_생성하지_않는다() {
        assertThatThrownBy(() -> InstructorMatchingSetting.create(
                instructorProfile(),
                Sport.SNOWBOARD,
                null,
                List.of(120),
                3,
                true
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("lessonLevels must not be empty.");
    }

    @Test
    void create는_lessonLevels에_null_원소가_있으면_생성하지_않는다() {
        assertThatThrownBy(() -> InstructorMatchingSetting.create(
                instructorProfile(),
                Sport.SNOWBOARD,
                Arrays.asList(LessonLevel.FIRST_TIME, null),
                List.of(120),
                3,
                true
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("lessonLevels must not contain null.");
    }

    @Test
    void create는_availableDurationMinutes가_비어있으면_생성하지_않는다() {
        assertThatThrownBy(() -> InstructorMatchingSetting.create(
                instructorProfile(),
                Sport.SNOWBOARD,
                List.of(LessonLevel.FIRST_TIME),
                List.of(),
                3,
                true
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("availableDurationMinutes must not be empty.");
    }

    @Test
    void create는_availableDurationMinutes가_null이면_생성하지_않는다() {
        assertThatThrownBy(() -> InstructorMatchingSetting.create(
                instructorProfile(),
                Sport.SNOWBOARD,
                List.of(LessonLevel.FIRST_TIME),
                null,
                3,
                true
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("availableDurationMinutes must not be empty.");
    }

    @Test
    void create는_availableDurationMinutes에_null_원소가_있으면_생성하지_않는다() {
        assertThatThrownBy(() -> InstructorMatchingSetting.create(
                instructorProfile(),
                Sport.SNOWBOARD,
                List.of(LessonLevel.FIRST_TIME),
                Arrays.asList(120, null),
                3,
                true
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("availableDurationMinutes must not contain null.");
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

}
