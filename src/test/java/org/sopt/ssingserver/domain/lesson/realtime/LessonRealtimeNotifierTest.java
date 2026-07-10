package org.sopt.ssingserver.domain.lesson.realtime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.lesson.dto.realtime.LessonRealtimeEvent;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

class LessonRealtimeNotifierTest {

    @Test
    void send는_memberId_문자열과_강습_개인큐로_전송한다() {
        SimpMessageSendingOperations messagingTemplate = mock(SimpMessageSendingOperations.class);
        LessonRealtimeNotifier notifier = new StompLessonRealtimeNotifier(messagingTemplate);
        LessonRealtimeEvent event = mock(LessonRealtimeEvent.class);

        notifier.send(new LessonRealtimeDelivery(12L, event));

        verify(messagingTemplate).convertAndSendToUser("12", "/queue/lesson", event);
    }
}
