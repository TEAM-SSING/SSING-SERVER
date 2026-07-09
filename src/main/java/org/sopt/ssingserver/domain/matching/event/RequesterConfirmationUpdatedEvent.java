package org.sopt.ssingserver.domain.matching.event;

import java.time.Instant;
import java.util.UUID;

// 그룹 대표 소비자 일부가 최종 확인을 완료한 진행 상황 이벤트
public record RequesterConfirmationUpdatedEvent(
        UUID eventId,
        Instant occurredAt,
        Long matchingRequestGroupId,
        Long matchingOfferId,
        int acceptedRequesterCount,
        int totalRequesterCount
) implements MatchingDomainEvent {
}
