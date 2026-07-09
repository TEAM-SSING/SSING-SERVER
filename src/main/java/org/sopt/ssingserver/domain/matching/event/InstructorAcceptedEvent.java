package org.sopt.ssingserver.domain.matching.event;

import java.time.Instant;
import java.util.UUID;

// 강사 수락 후 그룹 소비자들에게 최종 확인 시작을 알리는 도메인 이벤트
public record InstructorAcceptedEvent(
        UUID eventId,
        Instant occurredAt,
        Long matchingRequestGroupId,
        Long matchingOfferId
) implements MatchingDomainEvent {
}
