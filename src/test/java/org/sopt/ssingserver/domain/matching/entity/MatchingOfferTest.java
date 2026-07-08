package org.sopt.ssingserver.domain.matching.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;

class MatchingOfferTest {

    @Test
    void create는_강사제안을_OFFERED_상태로_초기화한다() {
        Instant exposedAt = Instant.parse("2026-07-07T00:00:00Z");
        Instant expiresAt = Instant.parse("2026-07-07T00:01:00Z");

        MatchingOffer offer = MatchingOffer.create(
                instructorProfile(),
                MatchingRequestGroup.createCandidate(120),
                exposedAt,
                expiresAt
        );

        assertThat(offer.getStatus()).isSameAs(MatchingOfferStatus.OFFERED);
        assertThat(offer.getExposedAt()).isEqualTo(exposedAt);
        assertThat(offer.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(offer.getRespondedAt()).isNull();
    }

    @Test
    void isExpired는_만료시각이_지났거나_같으면_true를_반환한다() {
        MatchingOffer offer = MatchingOffer.create(
                instructorProfile(),
                MatchingRequestGroup.createCandidate(120),
                Instant.parse("2026-07-07T00:00:00Z"),
                Instant.parse("2026-07-07T00:01:00Z")
        );

        assertThat(offer.isExpired(Instant.parse("2026-07-07T00:00:59Z"))).isFalse();
        assertThat(offer.isExpired(Instant.parse("2026-07-07T00:01:00Z"))).isTrue();
        assertThat(offer.isExpired(Instant.parse("2026-07-07T00:01:01Z"))).isTrue();
    }

    @Test
    void 응답_메서드는_OFFERED_상태에서만_상태와_응답시각을_저장한다() {
        MatchingOffer offer = MatchingOffer.create(
                instructorProfile(),
                MatchingRequestGroup.createCandidate(120),
                Instant.parse("2026-07-07T00:00:00Z"),
                Instant.parse("2026-07-07T00:01:00Z")
        );
        Instant respondedAt = Instant.parse("2026-07-07T00:01:00Z");

        offer.accept(respondedAt);
        assertThat(offer.getStatus()).isSameAs(MatchingOfferStatus.ACCEPTED);
        assertThat(offer.getRespondedAt()).isEqualTo(respondedAt);

        assertThatThrownBy(() -> offer.reject(respondedAt.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(offer.getStatus()).isSameAs(MatchingOfferStatus.ACCEPTED);
        assertThat(offer.getRespondedAt()).isEqualTo(respondedAt);
    }

    @Test
    void 닫기_메서드는_의도에_맞는_제안상태를_저장한다() {
        MatchingOffer offer = MatchingOffer.create(
                instructorProfile(),
                MatchingRequestGroup.createCandidate(120),
                Instant.parse("2026-07-07T00:00:00Z")
        );

        offer.cancel();
        assertThat(offer.getStatus()).isSameAs(MatchingOfferStatus.CANCELED);

        offer.expire();
        assertThat(offer.getStatus()).isSameAs(MatchingOfferStatus.EXPIRED);
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
}
