package org.sopt.ssingserver.domain.resort.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sopt.ssingserver.global.entity.BaseTimeEntity;

@Getter
@Entity
@Table(name = "resorts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Resort extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, unique = true)
    private String code;

    // 서버 내부 기준 정식 리조트명
    @Column(nullable = false, length = 100)
    private String name;

    // API 응답에서 code와 함께 내려줄 Android 표시용 리조트명
    @Column(nullable = false, length = 100)
    private String displayName;

    @PositiveOrZero
    @Column(nullable = false)
    private int passFeeAmount;

    public static Resort create(
            String code,
            String name,
            String displayName,
            int passFeeAmount
    ) {
        validatePassFeeAmount(passFeeAmount);

        Resort resort = new Resort();
        resort.code = code;
        resort.name = name;
        resort.displayName = displayName;
        resort.passFeeAmount = passFeeAmount;
        return resort;
    }

    private static void validatePassFeeAmount(int passFeeAmount) {
        if (passFeeAmount < 0) {
            throw new IllegalArgumentException("passFeeAmount must be non-negative.");
        }
    }
}
