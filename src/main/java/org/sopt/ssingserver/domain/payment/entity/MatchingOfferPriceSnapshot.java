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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sopt.ssingserver.domain.instructor.entity.InstructorPricePolicy;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "matching_offer_price_snapshots")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchingOfferPriceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private InstructorPricePolicy instructorPricePolicy;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private MatchingOffer matchingOffer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private PlatformFeePolicy platformFeePolicy;

    @Column(nullable = false)
    private int consumerTotalAmount;

    @Column(nullable = false)
    private int instructorSettlementAmount;

    @Column(nullable = false)
    private int totalHeadcount;

    @Column(nullable = false)
    private int basePriceAmount;

    @Column(nullable = false)
    private int additionalPersonPriceAmount;

    @Column(nullable = false)
    private int platformFeeAmount;

    @Column(nullable = false)
    private int feeRateBps;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static MatchingOfferPriceSnapshot create(
            MatchingOffer matchingOffer,
            InstructorPricePolicy instructorPricePolicy,
            PlatformFeePolicy platformFeePolicy,
            int totalHeadcount
    ) {
        // 강사 제안 수락 이후에도 금액 기준이 흔들리지 않도록 제안 시점 금액 보존
        validateTotalHeadcount(totalHeadcount);

        MatchingOfferPriceSnapshot snapshot = new MatchingOfferPriceSnapshot();
        snapshot.instructorPricePolicy = instructorPricePolicy;
        snapshot.matchingOffer = matchingOffer;
        snapshot.platformFeePolicy = platformFeePolicy;
        snapshot.totalHeadcount = totalHeadcount;
        snapshot.basePriceAmount = instructorPricePolicy.getBasePriceAmount();
        snapshot.additionalPersonPriceAmount = instructorPricePolicy.getAdditionalPersonPriceAmount();
        snapshot.consumerTotalAmount = snapshot.basePriceAmount
                + snapshot.additionalPersonPriceAmount * Math.max(0, totalHeadcount - 1);
        snapshot.platformFeeAmount = 0;
        snapshot.feeRateBps = platformFeePolicy.getFeeRateBps();
        snapshot.instructorSettlementAmount = snapshot.consumerTotalAmount - snapshot.platformFeeAmount;
        return snapshot;
    }

    private static void validateTotalHeadcount(int totalHeadcount) {
        if (totalHeadcount <= 0) {
            throw new IllegalArgumentException("totalHeadcount must be positive.");
        }
    }
}
