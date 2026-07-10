package org.sopt.ssingserver.domain.lesson.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StompLessonRealtimeNotifier implements LessonRealtimeNotifier {

    private static final String LESSON_QUEUE_DESTINATION = "/queue/lesson";

    private final SimpMessageSendingOperations messagingTemplate;

    @Override
    public void send(LessonRealtimeDelivery delivery) {
        messagingTemplate.convertAndSendToUser(
                delivery.recipientMemberId().toString(),
                LESSON_QUEUE_DESTINATION,
                delivery.event()
        );
    }
}
