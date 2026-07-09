package org.sopt.ssingserver.domain.matching.realtime;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sopt.ssingserver.domain.matching.event.MatchingDomainEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MatchingRealtimeEventPublisher implements MatchingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(MatchingRealtimeEventPublisher.class);

    private final MatchingRealtimeEventFactory matchingRealtimeEventFactory;
    private final MatchingRealtimeNotifier matchingRealtimeNotifier;

    @Override
    public void publish(MatchingDomainEvent event) {
        try {
            matchingRealtimeEventFactory.create(event)
                    .ifPresentOrElse(
                            matchingRealtimeNotifier::send,
                            () -> logSkippedEvent(event)
                    );
        } catch (RuntimeException exception) {
            // WebSocket 전송 실패는 커밋된 매칭 상태를 되돌리지 않고 운영 추적 로그로 남긴다.
            log.atWarn()
                    .addKeyValue("event", "matching.realtime.publish.failed")
                    .addKeyValue("matching_event_id", event.eventId().toString())
                    .addKeyValue("matching_event_type", event.getClass().getSimpleName())
                    .setCause(exception)
                    .log("Matching realtime event publish failed");
        }
    }

    private void logSkippedEvent(MatchingDomainEvent event) {
        log.atWarn()
                .addKeyValue("event", "matching.realtime.publish.skipped")
                .addKeyValue("matching_event_id", event.eventId().toString())
                .addKeyValue("matching_event_type", event.getClass().getSimpleName())
                .log("Matching realtime event context was not found");
    }
}
