package org.sopt.ssingserver.domain.matching.event;

// 강사 대상 매칭 제안 row 생성 알림용 도메인 이벤트
public record MatchingOfferCreatedEvent(
        Long matchingRequestId,
        Long matchingRequestGroupId,
        Long matchingOfferId,
        Long instructorProfileId
) implements MatchingDomainEvent {
}
