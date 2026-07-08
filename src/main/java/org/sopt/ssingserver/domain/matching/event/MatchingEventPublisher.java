package org.sopt.ssingserver.domain.matching.event;

// 매칭 Service의 WebSocket/FCM 구현 직접 의존 방지용 이벤트 발행 포트
public interface MatchingEventPublisher {

    // Service 생성 매칭 이벤트의 외부 알림 계층 전달 경계 메서드
    // DB 상태와 알림 상태 불일치 방지를 위한 Service 쪽 afterCommit 호출 시점 조정
    void publish(MatchingDomainEvent event);
}
