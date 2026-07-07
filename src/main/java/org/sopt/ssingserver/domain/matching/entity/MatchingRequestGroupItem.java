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
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupItemStatus;
import org.sopt.ssingserver.global.entity.BaseTimeEntity;

@Getter
@Entity
@Table(
        name = "matching_request_group_items",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_matching_request_group_items_group_request",
                        columnNames = {"matching_request_group_id", "matching_request_id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchingRequestGroupItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private MatchingRequest matchingRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private MatchingRequestGroup matchingRequestGroup;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MatchingRequestGroupItemStatus status;

    private Instant respondedAt;

    public static MatchingRequestGroupItem createNotRequested(
            MatchingRequest matchingRequest,
            MatchingRequestGroup matchingRequestGroup
    ) {
        MatchingRequestGroupItem item = new MatchingRequestGroupItem();
        item.matchingRequest = matchingRequest;
        item.matchingRequestGroup = matchingRequestGroup;
        item.status = MatchingRequestGroupItemStatus.NOT_REQUESTED;
        return item;
    }

    // TODO: PR 5 상태 전이 API에서 NOT_REQUESTED/PENDING 규칙과 종료 상태 guard 추가
    public void requestConfirmation() {
        this.status = MatchingRequestGroupItemStatus.PENDING;
    }

    public void accept(Instant respondedAt) {
        respond(MatchingRequestGroupItemStatus.ACCEPTED, respondedAt);
    }

    public void reject(Instant respondedAt) {
        respond(MatchingRequestGroupItemStatus.REJECTED, respondedAt);
    }

    public void cancel() {
        this.status = MatchingRequestGroupItemStatus.CANCELED;
    }

    public void expire() {
        this.status = MatchingRequestGroupItemStatus.EXPIRED;
    }

    private void respond(MatchingRequestGroupItemStatus status, Instant respondedAt) {
        this.status = status;
        this.respondedAt = respondedAt;
    }
}
