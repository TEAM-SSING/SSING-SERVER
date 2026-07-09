package org.sopt.ssingserver.domain.matching.dto.result;

import java.time.Instant;
import java.util.List;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;

public record InstructorMatchingOffersResult(
        List<ItemResult> items,
        int currentPage,
        int size,
        boolean hasNext
) {

    public record ItemResult(
            Long offerId,
            Long groupId,
            MatchingOfferStatus offerStatus,
            Instant expiresAt,
            LessonSummaryResult lessonSummary
    ) {
    }

    public record LessonSummaryResult(
            ResortResult resort,
            Sport sport,
            LessonLevel lessonLevel,
            int headcount,
            int durationMinutes
    ) {
    }

    public record ResortResult(
            String code,
            String displayName
    ) {
    }
}
