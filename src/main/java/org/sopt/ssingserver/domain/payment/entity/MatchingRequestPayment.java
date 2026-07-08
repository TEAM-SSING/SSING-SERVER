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
@Table(name = "matching_request_payments")
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

    @Column(nullable = false)
    private Instant paymentExpiresAt;

    private Instant paidAt;

    private Instant canceledAt;

    // 매칭 중지 API에서 아직 완료되지 않은 결제 요청을 함께 종료할 때 사용
    public void cancel(Instant canceledAt) {
        this.status = MatchingRequestPaymentStatus.CANCELED;
        this.canceledAt = canceledAt;
    }
}
