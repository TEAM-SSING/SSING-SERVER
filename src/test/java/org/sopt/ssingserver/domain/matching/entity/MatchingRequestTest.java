package org.sopt.ssingserver.domain.matching.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.springframework.test.util.ReflectionTestUtils;

class MatchingRequestTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-07-07T00:10:00Z");

    @Test
    void create는_매칭요청을_REQUESTED_상태로_초기화한다() {
        MatchingRequest matchingRequest = matchingRequest();

        assertThat(matchingRequest.getMember()).isNotNull();
        assertThat(matchingRequest.getResort()).isNotNull();
        assertThat(matchingRequest.getSport()).isSameAs(Sport.SNOWBOARD);
        assertThat(matchingRequest.getLessonLevel()).isSameAs(LessonLevel.FIRST_TIME);
        assertThat(matchingRequest.getHeadcount()).isEqualTo(2);
        assertThat(matchingRequest.getDurationMinutes()).isEqualTo(120);
        assertThat(matchingRequest.isEquipmentReady()).isTrue();
        assertThat(matchingRequest.getStatus()).isSameAs(MatchingRequestStatus.REQUESTED);
        assertThat(matchingRequest.getStatusReason()).isNull();
        assertThat(matchingRequest.getExpiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    void failNoAvailableInstructor는_FAILED와_후보없음_사유를_저장한다() {
        MatchingRequest matchingRequest = matchingRequest();

        matchingRequest.failNoAvailableInstructor();

        assertThat(matchingRequest.getStatus()).isSameAs(MatchingRequestStatus.FAILED);
        assertThat(matchingRequest.getStatusReason())
                .isSameAs(MatchingRequestStatusReason.NO_AVAILABLE_INSTRUCTOR);
    }

    @Test
    void cancelByConsumer는_CANCELED와_소비자취소_사유를_저장한다() {
        MatchingRequest matchingRequest = matchingRequest();

        matchingRequest.cancelByConsumer();

        assertThat(matchingRequest.getStatus()).isSameAs(MatchingRequestStatus.CANCELED);
        assertThat(matchingRequest.getStatusReason())
                .isSameAs(MatchingRequestStatusReason.CONSUMER_CANCELED);
    }

    @Test
    void markMatched는_수락된_제안과_확인만료시각을_저장한다() {
        MatchingRequest matchingRequest = matchingRequest();
        MatchingOffer matchingOffer = MatchingOffer.create(
                instructorProfile(),
                MatchingRequestGroup.createCandidate(),
                Instant.parse("2026-07-07T00:01:00Z")
        );
        Instant confirmationExpiresAt = Instant.parse("2026-07-07T00:02:00Z");

        matchingRequest.markMatched(matchingOffer, confirmationExpiresAt);

        assertThat(matchingRequest.getStatus()).isSameAs(MatchingRequestStatus.MATCHED);
        assertThat(matchingRequest.getMatchingOffer()).isSameAs(matchingOffer);
        assertThat(matchingRequest.getExpiresAt()).isEqualTo(confirmationExpiresAt);
        assertThat(matchingRequest.getStatusReason()).isNull();
    }

    @Test
    void 상태변경_메서드는_의도에_맞는_요청상태를_저장한다() {
        MatchingRequest matchingRequest = matchingRequest();

        matchingRequest.markGrouped();
        assertThat(matchingRequest.getStatus()).isSameAs(MatchingRequestStatus.GROUPED);

        matchingRequest.confirm();
        assertThat(matchingRequest.getStatus()).isSameAs(MatchingRequestStatus.CONFIRMED);

        matchingRequest.complete();
        assertThat(matchingRequest.getStatus()).isSameAs(MatchingRequestStatus.COMPLETED);

        matchingRequest.expire(MatchingRequestStatusReason.CONFIRMATION_TIMEOUT);
        assertThat(matchingRequest.getStatus()).isSameAs(MatchingRequestStatus.EXPIRED);
        assertThat(matchingRequest.getStatusReason())
                .isSameAs(MatchingRequestStatusReason.CONFIRMATION_TIMEOUT);
    }

    private MatchingRequest matchingRequest() {
        return MatchingRequest.create(
                Member.create("소비자", null, MemberRole.CONSUMER, MemberStatus.ACTIVE),
                resort(),
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                2,
                120,
                true,
                EXPIRES_AT
        );
    }

    private InstructorProfile instructorProfile() {
        try {
            Constructor<InstructorProfile> constructor = InstructorProfile.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Resort resort() {
        try {
            Constructor<Resort> constructor = Resort.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Resort resort = constructor.newInstance();
            ReflectionTestUtils.setField(resort, "code", "HIGH1");
            ReflectionTestUtils.setField(resort, "name", "하이원");
            return resort;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
