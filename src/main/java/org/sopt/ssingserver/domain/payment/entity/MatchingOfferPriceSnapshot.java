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
    private int resortPassFeeAmount;

    @Column(nullable = false)
    private int totalPaymentAmount;

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
            int totalHeadcount,
            int durationMinutes,
            int resortPassFeeAmount
    ) {
        // 강사 제안 수락 이후에도 금액 기준이 흔들리지 않도록 제안 시점 금액 보존
        validateTotalHeadcount(totalHeadcount);
        validateDurationMinutes(durationMinutes);
        validateResortPassFeeAmount(resortPassFeeAmount);

        MatchingOfferPriceSnapshot snapshot = new MatchingOfferPriceSnapshot();
        snapshot.instructorPricePolicy = instructorPricePolicy;
        snapshot.matchingOffer = matchingOffer;
        snapshot.platformFeePolicy = platformFeePolicy;
        snapshot.totalHeadcount = totalHeadcount;
        snapshot.basePriceAmount = instructorPricePolicy.getBasePriceAmount();
        snapshot.additionalPersonPriceAmount = instructorPricePolicy.getAdditionalPersonPriceAmount();
        long additionalPriceAmount = Math.multiplyExact(
                (long) snapshot.additionalPersonPriceAmount,
                Math.max(0, totalHeadcount - 1)
        );
        long priceFor120Minutes = Math.addExact((long) snapshot.basePriceAmount, additionalPriceAmount);
        snapshot.consumerTotalAmount = scaleByDuration(priceFor120Minutes, durationMinutes);
        snapshot.resortPassFeeAmount = resortPassFeeAmount;
        snapshot.totalPaymentAmount = Math.addExact(
                snapshot.consumerTotalAmount,
                snapshot.resortPassFeeAmount
        );
        snapshot.platformFeeAmount = 0;
        snapshot.feeRateBps = platformFeePolicy.getFeeRateBps();
        snapshot.instructorSettlementAmount = snapshot.consumerTotalAmount - snapshot.platformFeeAmount;
        return snapshot;
    }

    public int getLessonPriceAmount() {
        return consumerTotalAmount;
    }

    public int getTotalPaymentAmount() {
        // 새 총액 컬럼 추가 전 데이터는 기존 강습비를 총액으로 사용해 0원 응답 방지
        if (totalPaymentAmount == 0 && resortPassFeeAmount == 0 && consumerTotalAmount > 0) {
            return consumerTotalAmount;
        }
        return totalPaymentAmount;
    }

    private static void validateTotalHeadcount(int totalHeadcount) {
        if (totalHeadcount <= 0) {
            throw new IllegalArgumentException("totalHeadcount must be positive.");
        }
    }

    private static void validateDurationMinutes(int durationMinutes) {
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("durationMinutes must be positive.");
        }
    }

    private static int scaleByDuration(long priceFor120Minutes, int durationMinutes) {
        long numerator = Math.multiplyExact(priceFor120Minutes, durationMinutes);
        long quotient = numerator / 120;
        long remainder = numerator % 120;
        long roundedAmount = remainder * 2 >= 120 ? Math.addExact(quotient, 1) : quotient;
        return Math.toIntExact(roundedAmount);
    }

    private static void validateResortPassFeeAmount(int resortPassFeeAmount) {
        if (resortPassFeeAmount < 0) {
            throw new IllegalArgumentException("resortPassFeeAmount must be non-negative.");
        }
    }
}
