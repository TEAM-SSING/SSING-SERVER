package org.sopt.ssingserver.domain.lesson.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroup;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestParticipant;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.Gender;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.springframework.test.util.ReflectionTestUtils;

class LessonParticipantTest {

    @Test
    void create는_매칭참여자의_나이와_성별을_강습참여자정보로_보존한다() {
        Lesson lesson = immediateConfirmedLesson();
        MatchingRequest matchingRequest = matchingRequest();
        MatchingRequestParticipant matchingParticipant = MatchingRequestParticipant.create(
                matchingRequest,
                27,
                Gender.FEMALE
        );

        LessonParticipant lessonParticipant = LessonParticipant.create(
                lesson,
                matchingRequest,
                matchingParticipant
        );

        assertThat(lessonParticipant.getLesson()).isSameAs(lesson);
        assertThat(lessonParticipant.getMatchingRequest()).isSameAs(matchingRequest);
        assertThat(lessonParticipant.getMatchingRequestParticipant()).isSameAs(matchingParticipant);
        assertThat(lessonParticipant.getMember()).isNull();
        assertThat(lessonParticipant.getAge()).isEqualTo(27);
        assertThat(lessonParticipant.getGender()).isSameAs(Gender.FEMALE);
    }

    private MatchingRequest matchingRequest() {
        return MatchingRequest.create(
                Member.create("소비자", null, MemberRole.CONSUMER, MemberStatus.ACTIVE),
                resort(),
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                1,
                List.of(120),
                true
        );
    }

    private Lesson immediateConfirmedLesson() {
        InstructorProfile instructorProfile = approvedInstructorProfile();
        MatchingOffer matchingOffer = MatchingOffer.create(
                instructorProfile,
                MatchingRequestGroup.createCandidate(120),
                Instant.parse("2026-07-10T09:55:00Z")
        );
        return Lesson.createImmediateConfirmed(
                instructorProfile,
                resort(),
                matchingOffer,
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                1,
                120,
                Instant.parse("2026-07-10T10:00:00Z")
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
