package org.sopt.ssingserver.domain.instructor.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import org.springframework.stereotype.Component;

@Component
public class InstructorWaitingPriceEstimator {

    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final BigDecimal BASE_DURATION_MINUTES = BigDecimal.valueOf(120);
    private static final BigDecimal DISPLAY_ROUNDING_UNIT_AMOUNT = BigDecimal.valueOf(500);

    public int estimate(
            int basePriceAmount,
            int additionalPersonPriceAmount,
            Collection<Integer> availableDurationMinutes,
            int maxHeadcount
    ) {
        validateInputs(
                basePriceAmount,
                additionalPersonPriceAmount,
                availableDurationMinutes,
                maxHeadcount
        );

        int minimumDurationMinutes = availableDurationMinutes.stream()
                .mapToInt(Integer::intValue)
                .min()
                .orElseThrow();
        int maximumDurationMinutes = availableDurationMinutes.stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElseThrow();
        BigDecimal estimatedDurationMinutes = BigDecimal
                .valueOf((long) minimumDurationMinutes + maximumDurationMinutes)
                .divide(TWO);
        BigDecimal estimatedHeadcount = BigDecimal.valueOf(maxHeadcount)
                .divide(TWO)
                .max(BigDecimal.ONE);
        BigDecimal estimatedBaseDurationPrice = BigDecimal.valueOf(basePriceAmount)
                .add(BigDecimal.valueOf(additionalPersonPriceAmount)
                        .multiply(estimatedHeadcount.subtract(BigDecimal.ONE)));

        BigDecimal roundedUnitCount = estimatedBaseDurationPrice
                .multiply(estimatedDurationMinutes)
                .divide(
                        BASE_DURATION_MINUTES.multiply(DISPLAY_ROUNDING_UNIT_AMOUNT),
                        0,
                        RoundingMode.HALF_UP
                );
        return roundedUnitCount
                .multiply(DISPLAY_ROUNDING_UNIT_AMOUNT)
                .intValueExact();
    }

    private void validateInputs(
            int basePriceAmount,
            int additionalPersonPriceAmount,
            Collection<Integer> availableDurationMinutes,
            int maxHeadcount
    ) {
        if (basePriceAmount < 0 || additionalPersonPriceAmount < 0) {
            throw new IllegalArgumentException("price amounts must not be negative.");
        }
        if (maxHeadcount < 1) {
            throw new IllegalArgumentException("maxHeadcount must be positive.");
        }
        if (availableDurationMinutes == null || availableDurationMinutes.isEmpty()) {
            throw new IllegalArgumentException("availableDurationMinutes must not be empty.");
        }
        if (availableDurationMinutes.stream().anyMatch(duration -> duration == null || duration <= 0)) {
            throw new IllegalArgumentException("availableDurationMinutes must contain positive minutes.");
        }
    }
}
