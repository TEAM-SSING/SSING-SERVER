package org.sopt.ssingserver.domain.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "matching_request_price_snapshots",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_matching_request_price_snapshots_request_offer_snapshot",
                        columnNames = {"matching_request_id", "matching_offer_price_snapshot_id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchingRequestPriceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private MatchingRequest matchingRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private MatchingOfferPriceSnapshot matchingOfferPriceSnapshot;

    @Column(nullable = false)
    private int headcount;

    @Column(nullable = false)
    private int lessonPriceAmount;

    @Column(nullable = false)
    private int resortPassFeeAmount;

    @Column(name = "consumer_payment_amount", nullable = false)
    private int totalPaymentAmount;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static MatchingRequestPriceSnapshot create(
            MatchingRequest matchingRequest,
            MatchingOfferPriceSnapshot matchingOfferPriceSnapshot
    ) {
        MatchingRequestPriceSnapshot snapshot = new MatchingRequestPriceSnapshot();
        snapshot.matchingRequest = matchingRequest;
        snapshot.matchingOfferPriceSnapshot = matchingOfferPriceSnapshot;
        snapshot.headcount = matchingRequest.getHeadcount();
        snapshot.lessonPriceAmount = matchingOfferPriceSnapshot.getLessonPriceAmount();
        snapshot.resortPassFeeAmount = matchingOfferPriceSnapshot.getResortPassFeeAmount();
        snapshot.totalPaymentAmount = matchingOfferPriceSnapshot.getTotalPaymentAmount();
        return snapshot;
    }

    public int getLessonPriceAmount() {
        // 새 강습비 컬럼 추가 전 데이터는 기존 consumer_payment_amount를 강습비로 복구
        if (lessonPriceAmount == 0 && resortPassFeeAmount == 0 && totalPaymentAmount > 0) {
            return totalPaymentAmount;
        }
        return lessonPriceAmount;
    }
}
