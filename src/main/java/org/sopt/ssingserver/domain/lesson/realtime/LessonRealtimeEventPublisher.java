package org.sopt.ssingserver.domain.lesson.realtime;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LessonRealtimeEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LessonRealtimeEventPublisher.class);

    private final LessonRealtimeNotifier lessonRealtimeNotifier;

    public void publish(List<LessonRealtimeDelivery> deliveries) {
        if (deliveries.isEmpty()) {
            log.atWarn()
                    .addKeyValue("event", "lesson.realtime.publish.skipped")
                    .log("Lesson realtime event delivery was empty");
            return;
        }
        deliveries.forEach(this::send);
    }

    private void send(LessonRealtimeDelivery delivery) {
        try {
            lessonRealtimeNotifier.send(delivery);
        } catch (RuntimeException exception) {
            log.atWarn()
                    .addKeyValue("event", "lesson.realtime.publish.failed")
                    .addKeyValue("recipient_member_id", delivery.recipientMemberId().toString())
                    .addKeyValue("lesson_event_type", delivery.event().eventType().name())
                    .addKeyValue("exception_type", exception.getClass().getName())
                    .log("Lesson realtime event publish failed");
        }
    }
}
