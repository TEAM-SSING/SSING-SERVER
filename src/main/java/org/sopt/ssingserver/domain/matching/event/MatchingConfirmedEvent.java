package org.sopt.ssingserver.domain.matching.event;

import java.time.Instant;
import java.util.UUID;

// 그룹 결제 완료와 강습 생성까지 끝난 최종 확정 이벤트
public record MatchingConfirmedEvent(
        UUID eventId,
        Instant occurredAt,
        Long matchingRequestGroupId,
        Long matchingOfferId,
        Long lessonId
) implements MatchingDomainEvent {
}
