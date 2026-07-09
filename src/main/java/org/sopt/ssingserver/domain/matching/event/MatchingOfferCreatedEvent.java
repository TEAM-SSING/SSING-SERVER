package org.sopt.ssingserver.domain.matching.event;

import java.time.Instant;
import java.util.UUID;

// 강사 대상 매칭 제안 row 생성 알림용 도메인 이벤트
public record MatchingOfferCreatedEvent(
        // 알림 계층의 중복 발행 방어와 추적용 이벤트 id
        UUID eventId,
        // 제안 생성 판단 시각. 이벤트 순서 보장이 아니라 로그 추적에 사용한다.
        Instant occurredAt,
        // 소비자 요청 상태 복구 조회의 기준 매칭 요청 id
        Long matchingRequestId,
        // 강사에게 제안된 요청 묶음 id
        Long matchingRequestGroupId,
        // 강사 응답 API가 참조할 제안 row id
        Long matchingOfferId,
        // 요청/강사 가능 시간 교집합에서 이번 제안에 확정된 강습 시간
        int durationMinutes,
        // 알림 수신 강사 프로필 id
        Long instructorProfileId
) implements MatchingDomainEvent {
}
