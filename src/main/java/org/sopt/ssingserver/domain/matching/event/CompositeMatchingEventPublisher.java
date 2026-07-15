package org.sopt.ssingserver.domain.matching.event;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CompositeMatchingEventPublisher implements MatchingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(CompositeMatchingEventPublisher.class);

    private final List<MatchingEventHandler> handlers;

    // WebSocket과 FCM처럼 독립적인 후속 처리기를 순서대로 실행한다.
    @Override
    public void publish(MatchingDomainEvent event) {
        handlers.forEach(handler -> handle(handler, event));
    }

    // 한 처리기의 실패가 다른 채널의 이벤트 전달까지 막지 않도록 예외를 격리한다.
    private void handle(MatchingEventHandler handler, MatchingDomainEvent event) {
        try {
            handler.handle(event);
        } catch (RuntimeException exception) {
            log.atWarn()
                    .addKeyValue("event", "matching.event.handle.failed")
                    .addKeyValue("matching_event_id", event.eventId().toString())
                    .addKeyValue("matching_event_type", event.getClass().getSimpleName())
                    .addKeyValue("handler", handler.getClass().getSimpleName())
                    .addKeyValue("exception_type", exception.getClass().getName())
                    .log("Matching event handler failed");
        }
    }
}
