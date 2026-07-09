package org.sopt.ssingserver.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroupItem;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.payment.entity.MatchingRequestPayment;
import org.sopt.ssingserver.domain.payment.enums.MatchingRequestPaymentStatus;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.springframework.test.util.ReflectionTestUtils;

class MatchingStatusResolverTest {

    private final MatchingStatusResolver resolver = new MatchingStatusResolver();

    @Test
    void REQUESTED이고_후보도_그룹도_없으면_SEARCHING으로_계산한다() {
        MatchingStatus status = resolver.resolve(
                matchingRequest(1, 120),
                Optional.empty(),
                Optional.empty()
        );

        assertThat(status).isSameAs(MatchingStatus.SEARCHING);
    }

    @Test
    void REQUESTED이고_팀결합용_그룹이_있으면_WAITING_FOR_TEAM으로_계산한다() {
        MatchingRequestGroup group = MatchingRequestGroup.createCandidate(120);

        MatchingStatus status = resolver.resolve(
                matchingRequest(1, 120),
                Optional.of(group),
                Optional.empty()
        );

        assertThat(status).isSameAs(MatchingStatus.WAITING_FOR_TEAM);
    }

    @Test
    void 강사_제안이_생성되면_WAITING_FOR_INSTRUCTOR로_계산한다() {
        MatchingRequest matchingRequest = matchingRequest(1, 120);
        matchingRequest.markGrouped();
        MatchingRequestGroup group = MatchingRequestGroup.createCandidate(120);
        group.expose();
        MatchingOffer offer = MatchingOffer.create(
                instructorProfile(),
                group,
                Instant.parse("2026-07-07T00:00:00Z")
        );

        MatchingStatus status = resolver.resolve(
                matchingRequest,
                Optional.of(group),
                Optional.of(offer)
        );

        assertThat(status).isSameAs(MatchingStatus.WAITING_FOR_INSTRUCTOR);
    }

    @Test
    void 취소된_요청은_주변_객체보다_CANCELED로_먼저_계산한다() {
        MatchingRequest matchingRequest = matchingRequest(1, 120);
        matchingRequest.cancelByConsumer();
        MatchingRequestGroup group = MatchingRequestGroup.createCandidate(120);
        MatchingOffer offer = MatchingOffer.create(
                instructorProfile(),
                group,
                Instant.parse("2026-07-07T00:00:00Z")
        );

        MatchingStatus status = resolver.resolve(
                matchingRequest,
                Optional.of(group),
                Optional.of(offer)
        );

        assertThat(status).isSameAs(MatchingStatus.CANCELED);
    }

    @Test
    void 매칭_이후_요청상태는_소비자_화면용_다음_대기상태로_계산한다() {
        MatchingRequest matchedRequest = matchingRequest(1, 120);
        MatchingRequest confirmedRequest = matchingRequest(1, 120);
        MatchingRequest completedRequest = matchingRequest(1, 120);
        MatchingRequestGroup group = MatchingRequestGroup.createCandidate(120);
        MatchingOffer offer = MatchingOffer.create(
                instructorProfile(),
                group,
                Instant.parse("2026-07-07T00:00:00Z")
        );

        matchedRequest.markMatched(offer);
        confirmedRequest.confirm();
        completedRequest.complete();

        assertThat(resolver.resolve(matchedRequest, Optional.of(group), Optional.of(offer)))
                .isSameAs(MatchingStatus.WAITING_FOR_CONFIRMATION);
        assertThat(resolver.resolve(confirmedRequest, Optional.of(group), Optional.of(offer)))
                .isSameAs(MatchingStatus.CONFIRMED);
        assertThat(resolver.resolve(completedRequest, Optional.of(group), Optional.of(offer)))
                .isSameAs(MatchingStatus.CONFIRMED);
    }

    @Test
    void 소비자가_확정한_뒤_다른_참여자_확정을_기다리면_WAITING_FOR_OTHER_CONFIRMATIONS로_계산한다() {
        MatchingRequest matchingRequest = matchingRequest(1, 120);
        MatchingRequestGroup group = MatchingRequestGroup.createCandidate(120);
        MatchingRequestGroupItem item = MatchingRequestGroupItem.createNotRequested(matchingRequest, group);
        MatchingOffer offer = MatchingOffer.create(
                instructorProfile(),
                group,
                Instant.parse("2026-07-07T00:00:00Z")
        );
        item.accept(Instant.parse("2026-07-07T00:01:00Z"));
        matchingRequest.markMatched(offer);

        MatchingStatus status = resolver.resolve(
                matchingRequest,
                Optional.of(group),
                Optional.of(item),
                Optional.of(offer),
                Optional.empty()
        );

        assertThat(status).isSameAs(MatchingStatus.WAITING_FOR_OTHER_CONFIRMATIONS);
    }

    @Test
    void 결제_대기_row가_있으면_PAYMENT_PENDING으로_계산한다() {
        MatchingRequest matchingRequest = matchingRequest(1, 120);
        MatchingRequestGroup group = MatchingRequestGroup.createCandidate(120);
        MatchingOffer offer = MatchingOffer.create(
                instructorProfile(),
                group,
                Instant.parse("2026-07-07T00:00:00Z")
        );
        MatchingRequestPayment payment = matchingRequestPayment(MatchingRequestPaymentStatus.PENDING);
        matchingRequest.markMatched(offer);

        MatchingStatus status = resolver.resolve(
                matchingRequest,
                Optional.of(group),
                Optional.empty(),
                Optional.of(offer),
                Optional.of(payment)
        );

        assertThat(status).isSameAs(MatchingStatus.PAYMENT_PENDING);
    }

    @Test
    void 내_결제는_끝났지만_그룹이_결제대기이면_WAITING_FOR_OTHER_PAYMENTS로_계산한다() {
        MatchingRequest matchingRequest = matchingRequest(1, 120);
        MatchingRequestGroup group = MatchingRequestGroup.createCandidate(120);
        group.markPaymentPending();
        MatchingOffer offer = MatchingOffer.create(
                instructorProfile(),
                group,
                Instant.parse("2026-07-07T00:00:00Z")
        );
        MatchingRequestPayment payment = matchingRequestPayment(MatchingRequestPaymentStatus.COMPLETED);
        matchingRequest.markMatched(offer);

        MatchingStatus status = resolver.resolve(
                matchingRequest,
                Optional.of(group),
                Optional.empty(),
                Optional.of(offer),
                Optional.of(payment)
        );

        assertThat(status).isSameAs(MatchingStatus.WAITING_FOR_OTHER_PAYMENTS);
    }

    @Test
    void 결제가_만료되면_PAYMENT_EXPIRED로_계산한다() {
        MatchingRequest matchingRequest = matchingRequest(1, 120);
        MatchingRequestGroup group = MatchingRequestGroup.createCandidate(120);
        MatchingOffer offer = MatchingOffer.create(
                instructorProfile(),
                group,
                Instant.parse("2026-07-07T00:00:00Z")
        );
        MatchingRequestPayment payment = matchingRequestPayment(MatchingRequestPaymentStatus.EXPIRED);
        matchingRequest.markMatched(offer);

        MatchingStatus status = resolver.resolve(
                matchingRequest,
                Optional.of(group),
                Optional.empty(),
                Optional.of(offer),
                Optional.of(payment)
        );

        assertThat(status).isSameAs(MatchingStatus.PAYMENT_EXPIRED);
    }

    @Test
    void 결제시간초과_요청은_PAYMENT_EXPIRED로_계산한다() {
        MatchingRequest matchingRequest = matchingRequest(1, 120);
        matchingRequest.expireByPaymentTimeout();

        MatchingStatus status = resolver.resolve(
                matchingRequest,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );

        assertThat(status).isSameAs(MatchingStatus.PAYMENT_EXPIRED);
    }

    @Test
    void 강사거절이나_응답시간초과_요청은_REMATCHING으로_계산한다() {
        MatchingRequest matchingRequest = matchingRequest(1, 120);
        matchingRequest.expireByInstructorTimeout();

        MatchingStatus status = resolver.resolve(
                matchingRequest,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );

        assertThat(status).isSameAs(MatchingStatus.REMATCHING);
    }

    @Test
    void REQUESTED라도_이전_강사거절로_재탐색중이면_REMATCHING으로_계산한다() {
        MatchingRequest matchingRequest = matchingRequest(1, 120);
        matchingRequest.rematchAfterInstructorRejected();
        MatchingRequestGroup oldGroup = MatchingRequestGroup.createCandidate(120);
        oldGroup.cancel();

        MatchingStatus status = resolver.resolve(
                matchingRequest,
                Optional.of(oldGroup),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );

        assertThat(status).isSameAs(MatchingStatus.REMATCHING);
    }

    @Test
    void 후보없음_실패_요청은_NO_AVAILABLE_INSTRUCTOR로_계산한다() {
        MatchingRequest matchingRequest = matchingRequest(1, 120);
        matchingRequest.failNoAvailableInstructor();

        MatchingStatus status = resolver.resolve(
                matchingRequest,
                Optional.empty(),
                Optional.empty()
        );

        assertThat(status).isSameAs(MatchingStatus.NO_AVAILABLE_INSTRUCTOR);
    }

    @Test
    void 명시되지_않은_상태_조합은_기본_FAILED로_숨기지_않고_오류로_드러낸다() {
        MatchingRequest matchingRequest = matchingRequest(1, 120);
        matchingRequest.markGrouped();

        assertThatThrownBy(() -> resolver.resolve(
                matchingRequest,
                Optional.empty(),
                Optional.empty()
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isSameAs(CommonErrorCode.INTERNAL_ERROR));
    }

    private MatchingRequest matchingRequest(
            int headcount,
            int requestedDurationMinutes
    ) {
        return MatchingRequest.create(
                Member.create("소비자", null, MemberRole.CONSUMER, MemberStatus.ACTIVE),
                resort(),
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                headcount,
                List.of(requestedDurationMinutes),
                true
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

    private MatchingRequestPayment matchingRequestPayment(MatchingRequestPaymentStatus status) {
        try {
            Constructor<MatchingRequestPayment> constructor = MatchingRequestPayment.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            MatchingRequestPayment payment = constructor.newInstance();
            ReflectionTestUtils.setField(payment, "status", status);
            return payment;
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
