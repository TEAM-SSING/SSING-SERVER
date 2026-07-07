package org.sopt.ssingserver.domain.matching.event;

// 매칭 도메인 상태 변경/제안 생성 이벤트의 공통 계층
public sealed interface MatchingDomainEvent permits
        MatchingRequestStatusChangedEvent,
        MatchingOfferCreatedEvent {
}
