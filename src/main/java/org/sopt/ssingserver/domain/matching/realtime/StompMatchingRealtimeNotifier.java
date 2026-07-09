package org.sopt.ssingserver.domain.matching.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StompMatchingRealtimeNotifier implements MatchingRealtimeNotifier {

    private static final String MATCHING_QUEUE_DESTINATION = "/queue/matching";

    private final SimpMessageSendingOperations messagingTemplate;

    // 서버는 memberId 기준 개인 destination으로 보내고 클라이언트는 /user/queue/matching을 구독한다.
    @Override
    public void send(MatchingRealtimeDelivery delivery) {
        messagingTemplate.convertAndSendToUser(
                delivery.recipientMemberId().toString(),
                MATCHING_QUEUE_DESTINATION,
                delivery.event()
        );
    }
}
