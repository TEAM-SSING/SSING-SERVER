package org.sopt.ssingserver.domain.matching.event;

// WebSocket/FCM 전송을 의도적으로 끄는 테스트/수동 구성용 fallback 구현체
public class NoOpMatchingEventPublisher implements MatchingEventPublisher {

    // 알림 전송 비활성화 환경에서 의도적인 이벤트 무시
    @Override
    public void publish(MatchingDomainEvent event) {
    }
}
