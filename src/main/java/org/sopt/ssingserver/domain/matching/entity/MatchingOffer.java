package org.sopt.ssingserver.domain.matching.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.global.entity.BaseTimeEntity;

@Getter
@Entity
@Table(
        name = "matching_offers",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_matching_offers_group_instructor",
                        columnNames = {"matching_request_group_id", "instructor_profile_id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchingOffer extends BaseTimeEntity {

    private static final Duration DEFAULT_RESPONSE_TIMEOUT = Duration.ofMinutes(1);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private InstructorProfile instructorProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private MatchingRequestGroup matchingRequestGroup;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MatchingOfferStatus status;

    @Column(nullable = false)
    private Instant exposedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant respondedAt;

    public static MatchingOffer create(
            InstructorProfile instructorProfile,
            MatchingRequestGroup matchingRequestGroup,
            Instant exposedAt
    ) {
        return create(
                instructorProfile,
                matchingRequestGroup,
                exposedAt,
                exposedAt.plus(DEFAULT_RESPONSE_TIMEOUT)
        );
    }

    public static MatchingOffer create(
            InstructorProfile instructorProfile,
            MatchingRequestGroup matchingRequestGroup,
            Instant exposedAt,
            Instant expiresAt
    ) {
        MatchingOffer matchingOffer = new MatchingOffer();
        matchingOffer.instructorProfile = instructorProfile;
        matchingOffer.matchingRequestGroup = matchingRequestGroup;
        matchingOffer.status = MatchingOfferStatus.OFFERED;
        matchingOffer.exposedAt = exposedAt;
        matchingOffer.expiresAt = expiresAt;
        return matchingOffer;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void accept(Instant respondedAt) {
        respond(MatchingOfferStatus.ACCEPTED, respondedAt);
    }

    public void reject(Instant respondedAt) {
        respond(MatchingOfferStatus.REJECTED, respondedAt);
    }

    public void cancel() {
        this.status = MatchingOfferStatus.CANCELED;
    }

    public void expire() {
        this.status = MatchingOfferStatus.EXPIRED;
    }

    private void respond(MatchingOfferStatus status, Instant respondedAt) {
        if (this.status != MatchingOfferStatus.OFFERED) {
            throw new IllegalStateException("Only offered matching offer can be responded.");
        }

        this.status = status;
        this.respondedAt = respondedAt;
    }
}
