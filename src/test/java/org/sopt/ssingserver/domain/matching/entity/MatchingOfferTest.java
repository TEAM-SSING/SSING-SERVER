package org.sopt.ssingserver.domain.matching.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;

class MatchingOfferTest {

    @Test
    void create는_강사제안을_OFFERED_상태로_초기화한다() {
        Instant exposedAt = Instant.parse("2026-07-07T00:00:00Z");

        MatchingOffer offer = MatchingOffer.create(
                instructorProfile(),
                MatchingRequestGroup.createCandidate(120),
                exposedAt
        );

        assertThat(offer.getStatus()).isSameAs(MatchingOfferStatus.OFFERED);
        assertThat(offer.getExposedAt()).isEqualTo(exposedAt);
        assertThat(offer.getRespondedAt()).isNull();
    }

    @Test
    void 응답_메서드는_상태와_응답시각을_저장한다() {
        MatchingOffer offer = MatchingOffer.create(
                instructorProfile(),
                MatchingRequestGroup.createCandidate(120),
                Instant.parse("2026-07-07T00:00:00Z")
        );
        Instant respondedAt = Instant.parse("2026-07-07T00:01:00Z");

        offer.accept(respondedAt);
        assertThat(offer.getStatus()).isSameAs(MatchingOfferStatus.ACCEPTED);
        assertThat(offer.getRespondedAt()).isEqualTo(respondedAt);

        offer.reject(respondedAt.plusSeconds(1));
        assertThat(offer.getStatus()).isSameAs(MatchingOfferStatus.REJECTED);
        assertThat(offer.getRespondedAt()).isEqualTo(respondedAt.plusSeconds(1));
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
