package org.sopt.ssingserver.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.instructor.entity.InstructorPricePolicy;
import org.springframework.test.util.ReflectionTestUtils;

class MatchingOfferPriceSnapshotTest {

    @Test
    void getTotalPaymentAmount는_기존행의_consumerTotalAmount를_총액으로_호환한다() {
        MatchingOfferPriceSnapshot snapshot = newEntity(MatchingOfferPriceSnapshot.class);
        ReflectionTestUtils.setField(snapshot, "consumerTotalAmount", 80_000);
        ReflectionTestUtils.setField(snapshot, "resortPassFeeAmount", 0);
        ReflectionTestUtils.setField(snapshot, "totalPaymentAmount", 0);

        assertThat(snapshot.getLessonPriceAmount()).isEqualTo(80_000);
        assertThat(snapshot.getResortPassFeeAmount()).isZero();
        assertThat(snapshot.getTotalPaymentAmount()).isEqualTo(80_000);
    }

    @Test
    void getTotalPaymentAmount는_신규_0원_가격을_그대로_유지한다() {
        MatchingOfferPriceSnapshot snapshot = newEntity(MatchingOfferPriceSnapshot.class);
        ReflectionTestUtils.setField(snapshot, "consumerTotalAmount", 0);
        ReflectionTestUtils.setField(snapshot, "resortPassFeeAmount", 0);
        ReflectionTestUtils.setField(snapshot, "totalPaymentAmount", 0);

        assertThat(snapshot.getTotalPaymentAmount()).isZero();
    }

    @Test
    void create는_120분_가격을_인원수와_180분에_맞게_환산하고_패찰비를_한번만_더한다() {
        InstructorPricePolicy pricePolicy = pricePolicy(70_000, 10_000);
        PlatformFeePolicy platformFeePolicy = platformFeePolicy();

        MatchingOfferPriceSnapshot snapshot = MatchingOfferPriceSnapshot.create(
                null,
                pricePolicy,
                platformFeePolicy,
                2,
                180,
                30_000
        );

        assertThat(snapshot.getBasePriceAmount()).isEqualTo(70_000);
        assertThat(snapshot.getAdditionalPersonPriceAmount()).isEqualTo(10_000);
        assertThat(snapshot.getLessonPriceAmount()).isEqualTo(120_000);
        assertThat(snapshot.getInstructorSettlementAmount()).isEqualTo(120_000);
        assertThat(snapshot.getResortPassFeeAmount()).isEqualTo(30_000);
        assertThat(snapshot.getTotalPaymentAmount()).isEqualTo(150_000);
    }

    @Test
    void create는_180분_환산에서_소수원이_생기면_원단위로_반올림한다() {
        MatchingOfferPriceSnapshot snapshot = MatchingOfferPriceSnapshot.create(
                null,
                pricePolicy(10_001, 0),
                platformFeePolicy(),
                1,
                180,
                0
        );

        assertThat(snapshot.getLessonPriceAmount()).isEqualTo(15_002);
    }

    @Test
    void create는_추가인원이_있는_240분_가격을_두배로_계산한다() {
        MatchingOfferPriceSnapshot snapshot = MatchingOfferPriceSnapshot.create(
                null,
                pricePolicy(60_000, 20_000),
                platformFeePolicy(),
                3,
                240,
                25_000
        );

        assertThat(snapshot.getLessonPriceAmount()).isEqualTo(200_000);
        assertThat(snapshot.getInstructorSettlementAmount()).isEqualTo(200_000);
        assertThat(snapshot.getTotalPaymentAmount()).isEqualTo(225_000);
    }

    private InstructorPricePolicy pricePolicy(int basePriceAmount, int additionalPersonPriceAmount) {
        InstructorPricePolicy pricePolicy = newEntity(InstructorPricePolicy.class);
        ReflectionTestUtils.setField(pricePolicy, "basePriceAmount", basePriceAmount);
        ReflectionTestUtils.setField(pricePolicy, "additionalPersonPriceAmount", additionalPersonPriceAmount);
        return pricePolicy;
    }

    private PlatformFeePolicy platformFeePolicy() {
        PlatformFeePolicy platformFeePolicy = newEntity(PlatformFeePolicy.class);
        ReflectionTestUtils.setField(platformFeePolicy, "feeRateBps", 0);
        return platformFeePolicy;
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
