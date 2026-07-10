package org.sopt.ssingserver.domain.lesson.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroup;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.Gender;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.springframework.test.util.ReflectionTestUtils;

class LessonTest {

    @Test
    void createImmediateConfirmed는_확정시각을_일정시각으로_사용하고_CONFIRMED로_생성한다() {
        InstructorProfile instructorProfile = approvedInstructorProfile();
        Resort resort = resort();
        MatchingOffer matchingOffer = matchingOffer(instructorProfile);
        Instant confirmedAt = Instant.parse("2026-07-10T10:00:00Z");

        Lesson lesson = Lesson.createImmediateConfirmed(
                instructorProfile,
                resort,
                matchingOffer,
                Sport.SNOWBOARD,
                LessonLevel.BEGINNER,
                3,
                120,
                confirmedAt
        );

        assertThat(lesson.getInstructorProfile()).isSameAs(instructorProfile);
        assertThat(lesson.getResort()).isSameAs(resort);
        assertThat(lesson.getMatchingOffer()).isSameAs(matchingOffer);
        assertThat(lesson.getSport()).isSameAs(Sport.SNOWBOARD);
        assertThat(lesson.getLessonLevel()).isSameAs(LessonLevel.BEGINNER);
        assertThat(lesson.getTotalHeadcount()).isEqualTo(3);
        assertThat(lesson.getDurationMinutes()).isEqualTo(120);
        assertThat(lesson.getConfirmedAt()).isEqualTo(confirmedAt);
        assertThat(lesson.getScheduledAt()).isEqualTo(confirmedAt);
        assertThat(lesson.getStatus()).isSameAs(LessonStatus.CONFIRMED);
    }

    @Test
    void createImmediateConfirmed는_확정시각이_null이면_생성하지_않는다() {
        InstructorProfile instructorProfile = approvedInstructorProfile();

        assertThatThrownBy(() -> Lesson.createImmediateConfirmed(
                instructorProfile,
                resort(),
                matchingOffer(instructorProfile),
                Sport.SKI,
                LessonLevel.FIRST_TIME,
                1,
                120,
                null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("confirmedAt must not be null.");
    }

    private MatchingOffer matchingOffer(InstructorProfile instructorProfile) {
        return MatchingOffer.create(
                instructorProfile,
                MatchingRequestGroup.createCandidate(120),
                Instant.parse("2026-07-10T09:55:00Z")
        );
    }

    private InstructorProfile approvedInstructorProfile() {
        Member member = Member.create("승인강사", null, MemberRole.INSTRUCTOR, MemberStatus.ACTIVE);
        return InstructorProfile.create(
                member,
                "승인강사",
                "010-0000-0000",
                Gender.MALE,
                LocalDate.of(2000, 1, 1),
                "강사 소개",
                LocalDate.of(2020, 1, 1),
                InstructorApprovalStatus.APPROVED,
                Instant.parse("2026-07-01T00:00:00Z")
        );
    }

    private Resort resort() {
        Resort resort = newEntity(Resort.class);
        ReflectionTestUtils.setField(resort, "code", "HIGH1");
        ReflectionTestUtils.setField(resort, "name", "하이원리조트");
        ReflectionTestUtils.setField(resort, "displayName", "하이원");
        return resort;
    }

    private <T> T newEntity(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
