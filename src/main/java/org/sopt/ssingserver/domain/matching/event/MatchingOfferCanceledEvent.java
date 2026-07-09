package org.sopt.ssingserver.domain.matching.event;

import java.time.Instant;
import java.util.UUID;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;

// 소비자가 매칭을 완전히 중지해 강사에게 전달된 제안도 취소된 이벤트
public record MatchingOfferCanceledEvent(
        UUID eventId,
        Instant occurredAt,
        Long matchingRequestGroupId,
        Long matchingOfferId,
        MatchingRequestStatusReason requestStatusReason
) implements MatchingDomainEvent {
}
