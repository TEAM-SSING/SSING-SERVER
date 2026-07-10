package org.sopt.ssingserver.domain.matching.dto.result;

import java.time.Instant;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupItemStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;

public record ConsumerMatchingConfirmationResult(
        Long matchingRequestId,
        MatchingStatus matchingStatus,
        MatchingRequestGroupItemStatus confirmationStatus,
        MatchingRequestStatus requestStatus,
        MatchingRequestStatusReason requestStatusReason,
        Long groupId,
        MatchingRequestGroupStatus groupStatus,
        MatchingRequestGroupItemStatus itemStatus,
        Integer confirmedCount,
        Integer requiredCount,
        Instant expiresAt,
        MatchingPriceSummaryResult priceSummary
) {
}
