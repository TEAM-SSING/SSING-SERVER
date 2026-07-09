package org.sopt.ssingserver.domain.matching.event;

import java.time.Instant;
import java.util.UUID;

// 매칭 도메인 상태 변경/제안 생성 이벤트의 공통 계층
public sealed interface MatchingDomainEvent permits
        InstructorAcceptedEvent,
        MatchingConfirmedEvent,
        MatchingOfferCanceledEvent,
        MatchingOfferClosedEvent,
        MatchingRequestStatusChangedEvent,
        MatchingOfferCreatedEvent,
        PaymentPendingEvent,
        PaymentStatusChangedEvent,
        RequesterConfirmationUpdatedEvent {

    // WebSocket/FCM/로그 계층에서 이벤트 중복 처리와 추적에 사용할 고유 id
    UUID eventId();

    // DB 상태 변경 판단 시각과 알림 발행 시각의 기준 공유
    Instant occurredAt();
}
