package org.sopt.ssingserver.domain.matching.event;

import java.time.Instant;
import java.util.UUID;

// 그룹의 일부 결제가 완료되어 결제 진행률이 바뀐 이벤트
public record PaymentStatusChangedEvent(
        UUID eventId,
        Instant occurredAt,
        Long matchingRequestGroupId,
        Long matchingOfferId,
        int paidRequesterCount,
        int totalRequesterCount
) implements MatchingDomainEvent {
}
