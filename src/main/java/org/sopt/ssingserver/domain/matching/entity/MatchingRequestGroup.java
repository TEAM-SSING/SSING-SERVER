package org.sopt.ssingserver.domain.matching.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.global.entity.BaseTimeEntity;

@Getter
@Entity
@Table(name = "matching_request_groups")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchingRequestGroup extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MatchingRequestGroupStatus status;

    public static MatchingRequestGroup createCandidate() {
        MatchingRequestGroup matchingRequestGroup = new MatchingRequestGroup();
        matchingRequestGroup.status = MatchingRequestGroupStatus.CANDIDATE;
        return matchingRequestGroup;
    }

    public void expose() {
        this.status = MatchingRequestGroupStatus.EXPOSED;
    }

    public void markInstructorAccepted() {
        this.status = MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED;
    }

    public void markConsumerAccepted() {
        this.status = MatchingRequestGroupStatus.CONSUMER_ACCEPTED;
    }

    public void markPaymentPending() {
        this.status = MatchingRequestGroupStatus.PAYMENT_PENDING;
    }

    public void markPaymentExpired() {
        this.status = MatchingRequestGroupStatus.PAYMENT_EXPIRED;
    }

    public void confirm() {
        this.status = MatchingRequestGroupStatus.CONFIRMED;
    }

    public void lose() {
        this.status = MatchingRequestGroupStatus.LOST;
    }

    public void cancel() {
        this.status = MatchingRequestGroupStatus.CANCELED;
    }

    public void expire() {
        this.status = MatchingRequestGroupStatus.EXPIRED;
    }
}
