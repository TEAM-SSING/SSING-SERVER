package org.sopt.ssingserver.domain.matching.realtime;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sopt.ssingserver.domain.matching.event.MatchingDomainEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingEventHandler;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
@RequiredArgsConstructor
public class MatchingRealtimeEventPublisher implements MatchingEventHandler {

    private static final Logger log = LoggerFactory.getLogger(MatchingRealtimeEventPublisher.class);

    private final MatchingRealtimeEventFactory matchingRealtimeEventFactory;
    private final MatchingRealtimeNotifier matchingRealtimeNotifier;

    // Composite publisher가 호출하는 공통 이벤트 처리 진입점이다.
    @Override
    public void handle(MatchingDomainEvent event) {
        publish(event);
    }

    public void publish(MatchingDomainEvent event) {
        try {
            var deliveries = matchingRealtimeEventFactory.create(event);
            if (deliveries.isEmpty()) {
                logSkippedEvent(event);
                return;
            }
            deliveries.forEach(delivery -> send(event, delivery));
        } catch (RuntimeException exception) {
            logPublishFailure(event, null, "delivery-create", exception);
        }
    }

    // 한 수신자 전송 실패가 같은 이벤트의 다른 수신자 전송을 막지 않도록 수신자별로 격리한다.
    private void send(
            MatchingDomainEvent event,
            MatchingRealtimeDelivery delivery
    ) {
        try {
            matchingRealtimeNotifier.send(delivery);
        } catch (RuntimeException exception) {
            logPublishFailure(event, delivery.recipientMemberId(), "delivery-send", exception);
        }
    }

    private void logPublishFailure(
            MatchingDomainEvent event,
            Long recipientMemberId,
            String failureStage,
            RuntimeException exception
    ) {
        // WebSocket 전송 실패는 커밋된 매칭 상태를 되돌리지 않고 운영 추적 로그로 남긴다.
        log.atWarn()
                .addKeyValue("event", "matching.realtime.publish.failed")
                .addKeyValue("matching_event_id", event.eventId().toString())
                .addKeyValue("matching_event_type", event.getClass().getSimpleName())
                .addKeyValue("failure_stage", failureStage)
                .addKeyValue("recipient_member_id", recipientMemberId == null ? null : recipientMemberId.toString())
                .addKeyValue("exception_type", exception.getClass().getName())
                .addKeyValue("root_cause_type", rootCauseType(exception))
                .log("Matching realtime event publish failed");
    }

    private String rootCauseType(RuntimeException exception) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        return rootCause.getClass().getName();
    }

    private void logSkippedEvent(MatchingDomainEvent event) {
        log.atWarn()
                .addKeyValue("event", "matching.realtime.publish.skipped")
                .addKeyValue("matching_event_id", event.eventId().toString())
                .addKeyValue("matching_event_type", event.getClass().getSimpleName())
                .log("Matching realtime event context was not found");
    }
}
