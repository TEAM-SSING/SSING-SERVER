package org.sopt.ssingserver.domain.matching.dto.result;

import java.time.Instant;
import java.util.List;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;

public record InstructorMatchingOffersResult(
        Long offerId,
        MatchingSettingResult matchingSetting
) {

    public record MatchingSettingResult(
            boolean isExposed,
            ResortResult resort,
            Sport sport,
            List<LessonLevel> lessonLevels,
            List<Integer> availableDurationMinutes,
            int maxHeadcount,
            boolean equipmentReady,
            int estimatedLessonPriceAmount,
            PricePolicyResult pricePolicy
    ) {
    }

    public record PricePolicyResult(
            int basePriceAmount,
            int additionalPersonPriceAmount
    ) {
    }

    public record ItemResult(
            Long offerId,
            Long groupId,
            MatchingOfferStatus offerStatus,
            Instant expiresAt,
            RequestSummaryResult requestSummary,
            LessonSummaryResult lessonSummary,
            InstructorPriceSummaryResult priceSummary
    ) {
    }

    public record RequestSummaryResult(
            String requesterName,
            int headcount,
            int matchingRequestCount
    ) {
    }

    public record LessonSummaryResult(
            ResortResult resort,
            Sport sport,
            LessonLevel level,
            int durationMinutes,
            int totalHeadcount,
            String startType
    ) {
    }

    public record ResortResult(
            String code,
            String displayName
    ) {
    }
}
