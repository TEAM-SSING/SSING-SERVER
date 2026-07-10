package org.sopt.ssingserver.domain.payment.entity;

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
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.payment.enums.MatchingRequestPaymentStatus;
import org.sopt.ssingserver.global.entity.BaseTimeEntity;

@Getter
@Entity
@Table(
        name = "matching_request_payments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_matching_request_payments_request_offer",
                        columnNames = {"matching_request_id", "matching_offer_id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchingRequestPayment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private MatchingRequest matchingRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private MatchingRequestPriceSnapshot matchingRequestPriceSnapshot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private MatchingOffer matchingOffer;

    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MatchingRequestPaymentStatus status;

    @Column(nullable = false)
    private Instant paymentRequestedAt;

    private Instant paymentExpiresAt;

    private Instant paidAt;

    private Instant canceledAt;

    public static MatchingRequestPayment createPending(
            MatchingRequest matchingRequest,
            MatchingRequestPriceSnapshot matchingRequestPriceSnapshot,
            MatchingOffer matchingOffer,
            Instant paymentRequestedAt,
            Instant paymentExpiresAt
    ) {
        // MVP 무기한 결제 대기 정책의 paymentExpiresAt null 허용
        // 결제 금액을 외부 입력으로 받지 않고 요청 가격 스냅샷의 최종 금액으로만 생성
        MatchingRequestPayment payment = new MatchingRequestPayment();
        payment.matchingRequest = matchingRequest;
        payment.matchingRequestPriceSnapshot = matchingRequestPriceSnapshot;
        payment.matchingOffer = matchingOffer;
        payment.amount = matchingRequestPriceSnapshot.getTotalPaymentAmount();
        payment.status = MatchingRequestPaymentStatus.PENDING;
        payment.paymentRequestedAt = paymentRequestedAt;
        payment.paymentExpiresAt = paymentExpiresAt;
        return payment;
    }

    public void complete(Instant paidAt) {
        if (status != MatchingRequestPaymentStatus.PENDING) {
            throw new IllegalStateException("Only pending payment can be completed.");
        }

        this.status = MatchingRequestPaymentStatus.COMPLETED;
        this.paidAt = paidAt;
    }

    // 매칭 중지 API에서 PENDING 결제 요청을 함께 종료할 때 사용
    public void cancel(Instant canceledAt) {
        if (status != MatchingRequestPaymentStatus.PENDING) {
            throw new IllegalStateException("Only pending payment can be canceled.");
        }

        this.status = MatchingRequestPaymentStatus.CANCELED;
        this.canceledAt = canceledAt;
    }
}
