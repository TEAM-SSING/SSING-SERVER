package org.sopt.ssingserver.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.springframework.test.util.ReflectionTestUtils;

class MatchingRequestPriceSnapshotTest {

    @Test
    void create는_제안스냅샷의_강습비_패찰비_총액을_그대로_복사한다() {
        MatchingRequest matchingRequest = matchingRequest();
        MatchingOfferPriceSnapshot offerPriceSnapshot = offerPriceSnapshot();

        MatchingRequestPriceSnapshot snapshot = MatchingRequestPriceSnapshot.create(
                matchingRequest,
                offerPriceSnapshot
        );

        assertThat(snapshot.getMatchingRequest()).isSameAs(matchingRequest);
        assertThat(snapshot.getMatchingOfferPriceSnapshot()).isSameAs(offerPriceSnapshot);
        assertThat(snapshot.getHeadcount()).isEqualTo(2);
        assertThat(snapshot.getLessonPriceAmount()).isEqualTo(80_000);
        assertThat(snapshot.getResortPassFeeAmount()).isEqualTo(20_000);
        assertThat(snapshot.getTotalPaymentAmount()).isEqualTo(100_000);
    }

    @Test
    void getLessonPriceAmount는_기존행의_consumerPaymentAmount를_강습비로_호환한다() {
        MatchingRequestPriceSnapshot snapshot = newEntity(MatchingRequestPriceSnapshot.class);
        ReflectionTestUtils.setField(snapshot, "lessonPriceAmount", 0);
        ReflectionTestUtils.setField(snapshot, "resortPassFeeAmount", 0);
        ReflectionTestUtils.setField(snapshot, "totalPaymentAmount", 80_000);

        assertThat(snapshot.getLessonPriceAmount()).isEqualTo(80_000);
        assertThat(snapshot.getResortPassFeeAmount()).isZero();
        assertThat(snapshot.getTotalPaymentAmount()).isEqualTo(80_000);
    }

    @Test
    void getLessonPriceAmount는_신규_0원_강습과_유료_패찰비를_구분한다() {
        MatchingRequestPriceSnapshot snapshot = newEntity(MatchingRequestPriceSnapshot.class);
        ReflectionTestUtils.setField(snapshot, "lessonPriceAmount", 0);
        ReflectionTestUtils.setField(snapshot, "resortPassFeeAmount", 20_000);
        ReflectionTestUtils.setField(snapshot, "totalPaymentAmount", 20_000);

        assertThat(snapshot.getLessonPriceAmount()).isZero();
        assertThat(snapshot.getResortPassFeeAmount()).isEqualTo(20_000);
        assertThat(snapshot.getTotalPaymentAmount()).isEqualTo(20_000);
    }

    private MatchingOfferPriceSnapshot offerPriceSnapshot() {
        InstructorProfile instructorProfile = approvedInstructorProfile();
        MatchingOffer matchingOffer = MatchingOffer.create(
                instructorProfile,
                MatchingRequestGroup.createCandidate(120),
                Instant.parse("2026-07-10T10:00:00Z")
        );
        InstructorPricePolicy instructorPricePolicy = newEntity(InstructorPricePolicy.class);
        ReflectionTestUtils.setField(instructorPricePolicy, "instructorProfile", instructorProfile);
        ReflectionTestUtils.setField(instructorPricePolicy, "basePriceAmount", 70_000);
        ReflectionTestUtils.setField(instructorPricePolicy, "additionalPersonPriceAmount", 10_000);
        PlatformFeePolicy platformFeePolicy = newEntity(PlatformFeePolicy.class);
        ReflectionTestUtils.setField(platformFeePolicy, "feeRateBps", 0);

        return MatchingOfferPriceSnapshot.create(
                matchingOffer,
                instructorPricePolicy,
                platformFeePolicy,
                2,
                20_000
        );
    }

    private MatchingRequest matchingRequest() {
        return MatchingRequest.create(
                Member.create("소비자", null, MemberRole.CONSUMER, MemberStatus.ACTIVE),
                Resort.create("HIGH1", "하이원리조트", "하이원", 20_000),
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                2,
                List.of(120),
                true
        );
    }

    private InstructorProfile approvedInstructorProfile() {
        Member member = Member.create("강사", null, MemberRole.INSTRUCTOR, MemberStatus.ACTIVE);
        return InstructorProfile.create(
                member,
                "강사",
                "010-0000-0000",
                Gender.MALE,
                LocalDate.of(2000, 1, 1),
                "강사 소개",
                LocalDate.of(2020, 1, 1),
                InstructorApprovalStatus.APPROVED,
                Instant.parse("2026-07-10T09:00:00Z")
        );
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
