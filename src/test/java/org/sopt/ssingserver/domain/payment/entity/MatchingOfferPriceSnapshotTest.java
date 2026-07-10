package org.sopt.ssingserver.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;
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
