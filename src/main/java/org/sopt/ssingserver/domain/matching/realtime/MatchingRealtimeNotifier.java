package org.sopt.ssingserver.domain.matching.realtime;

// 매칭 알림 발행 흐름이 STOMP 구현체를 직접 알지 않도록 두는 전송 포트
public interface MatchingRealtimeNotifier {

    void send(MatchingRealtimeDelivery delivery);
}
