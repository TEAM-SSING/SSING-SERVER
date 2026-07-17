package org.sopt.ssingserver.domain.instructor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.Objects;
import org.sopt.ssingserver.global.entity.BaseTimeEntity;

@Getter
@Entity
@Table(name = "instructor_price_policies")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InstructorPricePolicy extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private InstructorProfile instructorProfile;

    @Column(nullable = false)
    private int basePriceAmount;

    @Column(nullable = false)
    private int additionalPersonPriceAmount;

    @Column(nullable = false)
    private boolean isActive;

    public static InstructorPricePolicy createActive(
            InstructorProfile instructorProfile,
            int basePriceAmount,
            int additionalPersonPriceAmount
    ) {
        validatePriceAmounts(basePriceAmount, additionalPersonPriceAmount);

        InstructorPricePolicy pricePolicy = new InstructorPricePolicy();
        pricePolicy.instructorProfile = Objects.requireNonNull(instructorProfile, "instructorProfile");
        pricePolicy.basePriceAmount = basePriceAmount;
        pricePolicy.additionalPersonPriceAmount = additionalPersonPriceAmount;
        pricePolicy.isActive = true;
        return pricePolicy;
    }

    public void deactivate() {
        isActive = false;
    }

    private static void validatePriceAmounts(int basePriceAmount, int additionalPersonPriceAmount) {
        if (basePriceAmount < 0 || additionalPersonPriceAmount < 0) {
            throw new IllegalArgumentException("Price amounts must be non-negative.");
        }
    }
}
