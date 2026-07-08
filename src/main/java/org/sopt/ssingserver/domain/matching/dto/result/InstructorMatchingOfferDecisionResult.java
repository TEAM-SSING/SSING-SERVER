package org.sopt.ssingserver.domain.matching.dto.result;

import java.time.Instant;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;

public record InstructorMatchingOfferDecisionResult(
        Long offerId,
        MatchingOfferStatus offerStatus,
        Long groupId,
        MatchingRequestGroupStatus groupStatus,
        Instant requesterConfirmationExpiresAt
) {
}
