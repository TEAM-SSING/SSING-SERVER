package org.sopt.ssingserver.domain.matching.realtime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

class MatchingRealtimeNotifierTest {

    @Test
    void send는_memberId_문자열과_매칭_개인큐로_전송한다() {
        SimpMessageSendingOperations messagingTemplate = mock(SimpMessageSendingOperations.class);
        MatchingRealtimeNotifier notifier = new MatchingRealtimeNotifier(messagingTemplate);
        MatchingRealtimeEvent event = mock(MatchingRealtimeEvent.class);

        notifier.send(new MatchingRealtimeDelivery(12L, event));

        verify(messagingTemplate).convertAndSendToUser("12", "/queue/matching", event);
    }
}
