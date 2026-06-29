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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int feeRateBps;

    @Column(nullable = false)
    private boolean isActive;
}
