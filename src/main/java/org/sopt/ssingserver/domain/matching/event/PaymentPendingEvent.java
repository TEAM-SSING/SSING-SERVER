package org.sopt.ssingserver.domain.matching.event;

import java.time.Instant;
import java.util.UUID;

// 모든 대표 소비자 수락 후 요청별 결제 대기가 시작된 이벤트
public record PaymentPendingEvent(
        UUID eventId,
        Instant occurredAt,
        Long matchingRequestGroupId,
        Long matchingOfferId
) implements MatchingDomainEvent {
}
