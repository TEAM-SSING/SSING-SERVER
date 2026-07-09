package org.sopt.ssingserver.domain.matching.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferCreatedEvent;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.support.MessageBuilder;

class MatchingRealtimeEventPublisherTest {

    private final MatchingRealtimeEventFactory factory = mock(MatchingRealtimeEventFactory.class);
    private final MatchingRealtimeNotifier notifier = mock(MatchingRealtimeNotifier.class);
    private final MatchingRealtimeEventPublisher publisher = new MatchingRealtimeEventPublisher(factory, notifier);

    @Test
    void publish는_factory가_만든_delivery를_notifier로_전달한다() {
        MatchingOfferCreatedEvent event = offerCreatedEvent();
        MatchingRealtimeDelivery delivery = new MatchingRealtimeDelivery(12L, mock(MatchingRealtimeEvent.class));
        when(factory.create(event)).thenReturn(List.of(delivery));

        publisher.publish(event);

        verify(notifier).send(delivery);
    }

    @Test
    void publish는_전송대상이_없으면_notifier를_호출하지_않는다() {
        MatchingOfferCreatedEvent event = offerCreatedEvent();
        when(factory.create(event)).thenReturn(List.of());

        publisher.publish(event);

        verifyNoInteractions(notifier);
    }

    @Test
    void publish는_한_수신자_전송실패가_다른_수신자의_전송을_막지_않는다() {
        MatchingOfferCreatedEvent event = offerCreatedEvent();
        MatchingRealtimeDelivery failedDelivery = new MatchingRealtimeDelivery(12L, mock(MatchingRealtimeEvent.class));
        MatchingRealtimeDelivery nextDelivery = new MatchingRealtimeDelivery(13L, mock(MatchingRealtimeEvent.class));
        when(factory.create(event)).thenReturn(List.of(failedDelivery, nextDelivery));
        MessageDeliveryException deliveryException = new MessageDeliveryException(
                MessageBuilder.withPayload("private-instructor-name").build(),
                new IllegalStateException("private-profile-url")
        );
        doThrow(deliveryException).when(notifier).send(failedDelivery);
        Logger logger = (Logger) LoggerFactory.getLogger(MatchingRealtimeEventPublisher.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            assertThatCode(() -> publisher.publish(event))
                    .doesNotThrowAnyException();
            verify(notifier).send(nextDelivery);
            verify(notifier, times(2)).send(org.mockito.ArgumentMatchers.any(MatchingRealtimeDelivery.class));

            ILoggingEvent logEvent = appender.list.getFirst();
            assertThat(logEvent.getLevel()).isEqualTo(Level.WARN);
            assertThat(logEvent.getThrowableProxy()).isNull();
            assertThat(logEvent.toString()).doesNotContain("private-instructor-name", "private-profile-url");
            assertThat(keyValueMap(logEvent))
                    .containsEntry("event", "matching.realtime.publish.failed")
                    .containsEntry("recipient_member_id", "12")
                    .containsEntry("exception_type", MessageDeliveryException.class.getName());
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void publish는_factory_실패를_호출자에게_전파하지_않는다() {
        MatchingOfferCreatedEvent event = offerCreatedEvent();
        when(factory.create(event)).thenThrow(new IllegalStateException("context load failed"));

        assertThatCode(() -> publisher.publish(event)).doesNotThrowAnyException();

        verifyNoInteractions(notifier);
    }

    private MatchingOfferCreatedEvent offerCreatedEvent() {
        return new MatchingOfferCreatedEvent(
                UUID.randomUUID(),
                Instant.parse("2026-07-07T00:00:00Z"),
                1L,
                2L,
                3L,
                120,
                4L
        );
    }

    private Map<String, Object> keyValueMap(ILoggingEvent event) {
        return event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(keyValuePair -> keyValuePair.key, keyValuePair -> keyValuePair.value));
    }
}
