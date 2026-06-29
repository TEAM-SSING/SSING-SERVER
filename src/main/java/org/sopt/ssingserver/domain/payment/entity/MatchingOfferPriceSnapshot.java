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
}
