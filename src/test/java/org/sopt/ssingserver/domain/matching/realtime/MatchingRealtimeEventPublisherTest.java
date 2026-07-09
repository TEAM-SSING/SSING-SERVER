package org.sopt.ssingserver.domain.matching.realtime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferCreatedEvent;

class MatchingRealtimeEventPublisherTest {

    private final MatchingRealtimeEventFactory factory = mock(MatchingRealtimeEventFactory.class);
    private final MatchingRealtimeNotifier notifier = mock(MatchingRealtimeNotifier.class);
    private final MatchingRealtimeEventPublisher publisher = new MatchingRealtimeEventPublisher(factory, notifier);

    @Test
    void publish는_factory가_만든_delivery를_notifier로_전달한다() {
        MatchingOfferCreatedEvent event = offerCreatedEvent();
        MatchingRealtimeDelivery delivery = new MatchingRealtimeDelivery(12L, mock(MatchingRealtimeEvent.class));
        when(factory.create(event)).thenReturn(Optional.of(delivery));

        publisher.publish(event);

        verify(notifier).send(delivery);
    }

    @Test
    void publish는_전송대상이_없으면_notifier를_호출하지_않는다() {
        MatchingOfferCreatedEvent event = offerCreatedEvent();
        when(factory.create(event)).thenReturn(Optional.empty());

        publisher.publish(event);

        verifyNoInteractions(notifier);
    }

    @Test
    void publish는_WebSocket_전송실패를_비즈니스_흐름으로_전파하지_않는다() {
        MatchingOfferCreatedEvent event = offerCreatedEvent();
        MatchingRealtimeDelivery delivery = new MatchingRealtimeDelivery(12L, mock(MatchingRealtimeEvent.class));
        when(factory.create(event)).thenReturn(Optional.of(delivery));
        doThrow(new IllegalStateException("send failed")).when(notifier).send(delivery);

        assertThatCode(() -> publisher.publish(event))
                .doesNotThrowAnyException();
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
}
