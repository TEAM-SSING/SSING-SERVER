package org.sopt.ssingserver.domain.matching.event;

import java.time.Instant;
import java.util.UUID;

// 거절·만료·그룹 종료로 강사 제안 카드가 닫힌 이벤트
public record MatchingOfferClosedEvent(
        UUID eventId,
        Instant occurredAt,
        Long matchingRequestGroupId,
        Long matchingOfferId,
        MatchingOfferClosedReason closedReason
) implements MatchingDomainEvent {
}
