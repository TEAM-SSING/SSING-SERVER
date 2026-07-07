package org.sopt.ssingserver.domain.matching.event;

import org.springframework.stereotype.Component;

// PR 1-B 범위의 실제 WebSocket/FCM 미전송 및 Service 의존성 충족용 기본 구현체
@Component
public class NoOpMatchingEventPublisher implements MatchingEventPublisher {

    // 후속 PR의 실제 알림 구현체 연결 전까지 의도적인 이벤트 무시
    @Override
    public void publish(MatchingDomainEvent event) {
    }
}
