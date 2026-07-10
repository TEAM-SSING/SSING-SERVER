package org.sopt.ssingserver.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.instructor.entity.InstructorPricePolicy;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroup;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.Gender;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.payment.enums.MatchingRequestPaymentStatus;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.springframework.test.util.ReflectionTestUtils;

class MatchingRequestPaymentTest {

    private static final int PAYMENT_AMOUNT = 80_000;
    private static final Instant REQUESTED_AT = Instant.parse("2026-07-10T10:00:00Z");

    @Test
    void createPending은_만료시각없이_PENDING_결제로_생성할_수_있다() {
        PaymentFixture fixture = pendingPayment();

        assertThat(fixture.payment().getMatchingRequest()).isSameAs(fixture.matchingRequest());
        assertThat(fixture.payment().getMatchingRequestPriceSnapshot()).isSameAs(fixture.requestPriceSnapshot());
        assertThat(fixture.payment().getMatchingOffer()).isSameAs(fixture.matchingOffer());
        assertThat(fixture.payment().getAmount()).isEqualTo(PAYMENT_AMOUNT);
        assertThat(fixture.payment().getStatus()).isSameAs(MatchingRequestPaymentStatus.PENDING);
        assertThat(fixture.payment().getPaymentRequestedAt()).isEqualTo(REQUESTED_AT);
        assertThat(fixture.payment().getPaymentExpiresAt()).isNull();
        assertThat(fixture.payment().getPaidAt()).isNull();
        assertThat(fixture.payment().getCanceledAt()).isNull();
    }

    @Test
    void complete는_PENDING_결제를_COMPLETED로_변경하고_결제시각을_저장한다() {
        MatchingRequestPayment payment = pendingPayment().payment();
        Instant paidAt = REQUESTED_AT.plusSeconds(30);

        payment.complete(paidAt);

        assertThat(payment.getStatus()).isSameAs(MatchingRequestPaymentStatus.COMPLETED);
        assertThat(payment.getPaidAt()).isEqualTo(paidAt);
    }

    @Test
    void complete는_PENDING이_아닌_결제를_다시_완료하지_않는다() {
        MatchingRequestPayment payment = pendingPayment().payment();
        Instant firstPaidAt = REQUESTED_AT.plusSeconds(30);
        payment.complete(firstPaidAt);

        assertThatThrownBy(() -> payment.complete(firstPaidAt.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only pending payment can be completed.");
        assertThat(payment.getStatus()).isSameAs(MatchingRequestPaymentStatus.COMPLETED);
        assertThat(payment.getPaidAt()).isEqualTo(firstPaidAt);
    }

    @Test
    void cancel은_PENDING_결제를_CANCELED로_변경하고_취소시각을_저장한다() {
        MatchingRequestPayment payment = pendingPayment().payment();
        Instant canceledAt = REQUESTED_AT.plusSeconds(30);

        payment.cancel(canceledAt);

        assertThat(payment.getStatus()).isSameAs(MatchingRequestPaymentStatus.CANCELED);
        assertThat(payment.getCanceledAt()).isEqualTo(canceledAt);
        assertThatThrownBy(() -> payment.complete(canceledAt.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only pending payment can be completed.");
    }

    @Test
    void cancel은_이미_CANCELED인_결제를_다시_취소하지_않는다() {
        MatchingRequestPayment payment = pendingPayment().payment();
        Instant firstCanceledAt = REQUESTED_AT.plusSeconds(30);
        payment.cancel(firstCanceledAt);

        assertThatThrownBy(() -> payment.cancel(firstCanceledAt.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only pending payment can be canceled.");
        assertThat(payment.getStatus()).isSameAs(MatchingRequestPaymentStatus.CANCELED);
        assertThat(payment.getCanceledAt()).isEqualTo(firstCanceledAt);
    }

    @Test
    void cancel은_COMPLETED_결제를_취소하지_않는다() {
        MatchingRequestPayment payment = pendingPayment().payment();
        Instant paidAt = REQUESTED_AT.plusSeconds(30);
        payment.complete(paidAt);

        assertThatThrownBy(() -> payment.cancel(paidAt.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only pending payment can be canceled.");
        assertThat(payment.getStatus()).isSameAs(MatchingRequestPaymentStatus.COMPLETED);
        assertThat(payment.getPaidAt()).isEqualTo(paidAt);
        assertThat(payment.getCanceledAt()).isNull();
    }

    private PaymentFixture pendingPayment() {
        MatchingRequest matchingRequest = matchingRequest();
        MatchingOffer matchingOffer = matchingOffer();
        MatchingRequestPriceSnapshot requestPriceSnapshot = MatchingRequestPriceSnapshot.create(
                matchingRequest,
                offerPriceSnapshot(matchingOffer),
                PAYMENT_AMOUNT
        );
        MatchingRequestPayment payment = MatchingRequestPayment.createPending(
                matchingRequest,
                requestPriceSnapshot,
                matchingOffer,
                PAYMENT_AMOUNT,
                REQUESTED_AT,
                null
        );
        return new PaymentFixture(payment, matchingRequest, requestPriceSnapshot, matchingOffer);
    }

    private MatchingRequest matchingRequest() {
        return MatchingRequest.create(
                Member.create("소비자", null, MemberRole.CONSUMER, MemberStatus.ACTIVE),
                resort(),
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                2,
                List.of(120),
                true
        );
    }

    private MatchingOfferPriceSnapshot offerPriceSnapshot(MatchingOffer matchingOffer) {
        InstructorPricePolicy instructorPricePolicy = newEntity(InstructorPricePolicy.class);
        ReflectionTestUtils.setField(instructorPricePolicy, "instructorProfile", matchingOffer.getInstructorProfile());
        ReflectionTestUtils.setField(instructorPricePolicy, "basePriceAmount", 70_000);
        ReflectionTestUtils.setField(instructorPricePolicy, "additionalPersonPriceAmount", 10_000);
        ReflectionTestUtils.setField(instructorPricePolicy, "isActive", true);

        PlatformFeePolicy platformFeePolicy = newEntity(PlatformFeePolicy.class);
        ReflectionTestUtils.setField(platformFeePolicy, "feeRateBps", 0);
        ReflectionTestUtils.setField(platformFeePolicy, "isActive", true);

        return MatchingOfferPriceSnapshot.create(
                matchingOffer,
                instructorPricePolicy,
                platformFeePolicy,
                2
        );
    }

    private MatchingOffer matchingOffer() {
        return MatchingOffer.create(
                approvedInstructorProfile(),
                MatchingRequestGroup.createCandidate(120),
                REQUESTED_AT.minusSeconds(60)
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

    private record PaymentFixture(
            MatchingRequestPayment payment,
            MatchingRequest matchingRequest,
            MatchingRequestPriceSnapshot requestPriceSnapshot,
            MatchingOffer matchingOffer
    ) {
    }
}
