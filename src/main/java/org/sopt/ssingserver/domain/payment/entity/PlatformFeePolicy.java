package org.sopt.ssingserver.domain.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sopt.ssingserver.global.entity.BaseTimeEntity;

@Getter
@Entity
@Table(name = "platform_fee_policies")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlatformFeePolicy extends BaseTimeEntity {

    // MVP에서는 수수료를 0원으로 계산하지만, seed.sql에서 active 0% 정책 row를 하나 이상 유지해야 한다.
    // 이 row가 없으면 강사 제안 생성 시 MATCHING_PRICE_POLICY_NOT_FOUND로 실패한다.

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int feeRateBps;

    @Column(nullable = false)
    private boolean isActive;
}
