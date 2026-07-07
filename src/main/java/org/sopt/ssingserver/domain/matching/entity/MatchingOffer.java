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

    private Instant respondedAt;

    public static MatchingOffer create(
            InstructorProfile instructorProfile,
            MatchingRequestGroup matchingRequestGroup,
            Instant exposedAt
    ) {
        MatchingOffer matchingOffer = new MatchingOffer();
        matchingOffer.instructorProfile = instructorProfile;
        matchingOffer.matchingRequestGroup = matchingRequestGroup;
        matchingOffer.status = MatchingOfferStatus.OFFERED;
        matchingOffer.exposedAt = exposedAt;
        return matchingOffer;
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
        this.status = status;
        this.respondedAt = respondedAt;
    }
}
