package org.sopt.ssingserver.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroup;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.springframework.test.util.ReflectionTestUtils;

class MatchingStatusResolverTest {

    private final MatchingStatusResolver resolver = new MatchingStatusResolver();

    @Test
    void REQUESTED이고_후보도_그룹도_없으면_SEARCHING으로_계산한다() {
        MatchingStatus status = resolver.resolve(
                matchingRequest(1, 120, Instant.parse("2026-07-07T00:05:00Z")),
                false,
                Optional.empty(),
                Optional.empty()
        );

        assertThat(status).isSameAs(MatchingStatus.SEARCHING);
    }

    @Test
    void REQUESTED이고_후보는_있지만_그룹과_제안이_없으면_SEARCHING으로_계산한다() {
        MatchingStatus status = resolver.resolve(
                matchingRequest(1, 120, Instant.parse("2026-07-07T00:05:00Z")),
                true,
                Optional.empty(),
                Optional.empty()
        );

        assertThat(status).isSameAs(MatchingStatus.SEARCHING);
    }

    @Test
    void 강사_제안이_생성되면_WAITING_FOR_INSTRUCTOR로_계산한다() {
        MatchingRequestGroup group = MatchingRequestGroup.createCandidate(120);
        group.expose();
        MatchingOffer offer = MatchingOffer.create(
                instructorProfile(),
                group,
                Instant.parse("2026-07-07T00:00:00Z")
        );

        MatchingStatus status = resolver.resolve(
                matchingRequest(1, 120, Instant.parse("2026-07-07T00:05:00Z")),
                true,
                Optional.of(group),
                Optional.of(offer)
        );

        assertThat(status).isSameAs(MatchingStatus.WAITING_FOR_INSTRUCTOR);
    }

    @Test
    void 취소된_요청은_주변_객체보다_CANCELED로_먼저_계산한다() {
        MatchingRequest matchingRequest = matchingRequest(1, 120, Instant.parse("2026-07-07T00:05:00Z"));
        matchingRequest.cancelByConsumer();
        MatchingRequestGroup group = MatchingRequestGroup.createCandidate(120);
        MatchingOffer offer = MatchingOffer.create(
                instructorProfile(),
                group,
                Instant.parse("2026-07-07T00:00:00Z")
        );

        MatchingStatus status = resolver.resolve(
                matchingRequest,
                true,
                Optional.of(group),
                Optional.of(offer)
        );

        assertThat(status).isSameAs(MatchingStatus.CANCELED);
    }

    @Test
    void 매칭_이후_요청상태는_소비자_화면용_다음_대기상태로_계산한다() {
        MatchingRequest matchedRequest = matchingRequest(1, 120, Instant.parse("2026-07-07T00:05:00Z"));
        MatchingRequest confirmedRequest = matchingRequest(1, 120, Instant.parse("2026-07-07T00:05:00Z"));
        MatchingRequest completedRequest = matchingRequest(1, 120, Instant.parse("2026-07-07T00:05:00Z"));

        matchedRequest.markMatched(
                MatchingOffer.create(
                        instructorProfile(),
                        MatchingRequestGroup.createCandidate(120),
                        Instant.parse("2026-07-07T00:00:00Z")
                ),
                Instant.parse("2026-07-07T00:10:00Z")
        );
        confirmedRequest.confirm();
        completedRequest.complete();

        assertThat(resolver.resolve(matchedRequest, false, Optional.empty(), Optional.empty()))
                .isSameAs(MatchingStatus.WAITING_FOR_CONFIRMATION);
        assertThat(resolver.resolve(confirmedRequest, false, Optional.empty(), Optional.empty()))
                .isSameAs(MatchingStatus.PAYMENT_PENDING);
        assertThat(resolver.resolve(completedRequest, false, Optional.empty(), Optional.empty()))
                .isSameAs(MatchingStatus.CONFIRMED);
    }

    @Test
    void 후보없음_실패_요청은_NO_AVAILABLE_INSTRUCTOR로_계산한다() {
        MatchingRequest matchingRequest = matchingRequest(1, 120, Instant.parse("2026-07-07T00:05:00Z"));
        matchingRequest.failNoAvailableInstructor();

        MatchingStatus status = resolver.resolve(
                matchingRequest,
                false,
                Optional.empty(),
                Optional.empty()
        );

        assertThat(status).isSameAs(MatchingStatus.NO_AVAILABLE_INSTRUCTOR);
    }

    private MatchingRequest matchingRequest(
            int headcount,
            int requestedDurationMinutes,
            Instant expiresAt
    ) {
        return MatchingRequest.create(
                Member.create("소비자", null, MemberRole.CONSUMER, MemberStatus.ACTIVE),
                resort(),
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                headcount,
                List.of(requestedDurationMinutes),
                true,
                expiresAt
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
            ReflectionTestUtils.setField(resort, "name", "하이원리조트");
            ReflectionTestUtils.setField(resort, "displayName", "하이원");
            return resort;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
