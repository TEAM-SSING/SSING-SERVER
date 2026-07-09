package org.sopt.ssingserver.domain.matching.dto.result;

import java.time.Instant;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.payment.enums.MatchingRequestPaymentStatus;

public record ConsumerMatchingPaymentResult(
        Long matchingRequestId,
        MatchingStatus matchingStatus,
        MatchingRequestPaymentStatus paymentStatus,
        Long groupId,
        MatchingRequestGroupStatus groupStatus,
        int paidCount,
        int requiredCount,
        Long lessonId,
        Instant expiresAt
) {
}
