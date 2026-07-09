package org.sopt.ssingserver.domain.matching.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent.LessonSummary;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent.MatchingOfferReceivedPayload;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent.MatchingStatusPayload;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent.RequestSummary;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEventType;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeRecipientRole;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferCreatedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingRequestStatusChangedEvent;
import org.sopt.ssingserver.domain.matching.realtime.MatchingNotificationContextLoader.MatchingOfferReceivedContext;
import org.sopt.ssingserver.domain.matching.realtime.MatchingNotificationContextLoader.MatchingStatusChangedContext;

class MatchingRealtimeEventFactoryTest {

    private final MatchingNotificationContextLoader contextLoader = org.mockito.Mockito.mock(
            MatchingNotificationContextLoader.class
    );
    private final MatchingRealtimeEventFactory factory = new MatchingRealtimeEventFactory(contextLoader);

    @Test
    void create는_강사_제안생성_이벤트를_MATCHING_OFFER_RECEIVED_payload로_변환한다() {
        UUID eventId = UUID.fromString("b1e2a7d8-2259-46bd-80d2-f1e4e924e100");
        MatchingOfferCreatedEvent event = new MatchingOfferCreatedEvent(
                eventId,
                Instant.parse("2026-07-07T00:00:00Z"),
                10L,
                20L,
                30L,
                120,
                40L
        );
        when(contextLoader.load(event)).thenReturn(Optional.of(new MatchingOfferReceivedContext(
                99L,
                "홍길동",
                3,
                2,
                "하이원",
                "SNOWBOARD",
                "FIRST_TIME",
                120,
                4,
                "IMMEDIATE"
        )));

        Optional<MatchingRealtimeDelivery> result = factory.create(event);

        assertThat(result).isPresent();
        MatchingRealtimeDelivery delivery = result.get();
        assertThat(delivery.recipientMemberId()).isEqualTo(99L);
        MatchingRealtimeEvent realtimeEvent = delivery.event();
        assertThat(realtimeEvent.eventId()).isEqualTo(eventId);
        assertThat(realtimeEvent.eventType()).isSameAs(MatchingRealtimeEventType.MATCHING_OFFER_RECEIVED);
        assertThat(realtimeEvent.occurredAt().toString()).isEqualTo("2026-07-07T09:00+09:00");
        assertThat(realtimeEvent.recipientRole()).isSameAs(MatchingRealtimeRecipientRole.INSTRUCTOR);
        assertThat(realtimeEvent.matchingRequestId()).isNull();
        assertThat(realtimeEvent.groupId()).isEqualTo(20L);
        assertThat(realtimeEvent.offerId()).isEqualTo(30L);

        MatchingOfferReceivedPayload payload = (MatchingOfferReceivedPayload) realtimeEvent.payload();
        RequestSummary requestSummary = payload.requestSummary();
        LessonSummary lessonSummary = payload.lessonSummary();
        assertThat(requestSummary.requesterName()).isEqualTo("홍길동");
        assertThat(requestSummary.headcount()).isEqualTo(3);
        assertThat(requestSummary.matchingRequestCount()).isEqualTo(2);
        assertThat(lessonSummary.resortName()).isEqualTo("하이원");
        assertThat(lessonSummary.sport()).isEqualTo("SNOWBOARD");
        assertThat(lessonSummary.level()).isEqualTo("FIRST_TIME");
        assertThat(lessonSummary.durationMinutes()).isEqualTo(120);
        assertThat(lessonSummary.totalHeadcount()).isEqualTo(4);
        assertThat(lessonSummary.startType()).isEqualTo("IMMEDIATE");
    }

    @ParameterizedTest
    @MethodSource("statusChangedCases")
    void create는_상태변경_이벤트를_matchingStatus에_맞는_eventType과_message로_변환한다(
            MatchingStatus matchingStatus,
            MatchingRequestStatusReason statusReason,
            MatchingRealtimeEventType expectedEventType,
            String expectedMessage
    ) {
        UUID eventId = UUID.fromString("6f095f53-7a03-40d9-8942-4de5ec297001");
        MatchingRequestStatusChangedEvent event = new MatchingRequestStatusChangedEvent(
                eventId,
                Instant.parse("2026-07-07T00:00:00Z"),
                10L,
                MatchingRequestStatus.FAILED,
                statusReason,
                matchingStatus
        );
        when(contextLoader.load(event)).thenReturn(Optional.of(new MatchingStatusChangedContext(88L)));

        Optional<MatchingRealtimeDelivery> result = factory.create(event);

        assertThat(result).isPresent();
        MatchingRealtimeEvent realtimeEvent = result.get().event();
        assertThat(result.get().recipientMemberId()).isEqualTo(88L);
        assertThat(realtimeEvent.eventType()).isSameAs(expectedEventType);
        assertThat(realtimeEvent.recipientRole()).isSameAs(MatchingRealtimeRecipientRole.CONSUMER);
        assertThat(realtimeEvent.matchingRequestId()).isEqualTo(10L);
        assertThat(realtimeEvent.matchingStatus()).isSameAs(matchingStatus);

        MatchingStatusPayload payload = (MatchingStatusPayload) realtimeEvent.payload();
        assertThat(payload.message()).isEqualTo(expectedMessage);
        assertThat(payload.requestStatusReason()).isSameAs(statusReason);
    }

    @Test
    void create는_조회_context가_없으면_전송대상을_만들지_않는다() {
        MatchingOfferCreatedEvent event = new MatchingOfferCreatedEvent(
                UUID.randomUUID(),
                Instant.parse("2026-07-07T00:00:00Z"),
                10L,
                20L,
                30L,
                120,
                40L
        );
        when(contextLoader.load(event)).thenReturn(Optional.empty());

        Optional<MatchingRealtimeDelivery> result = factory.create(event);

        assertThat(result).isEmpty();
    }

    private static Stream<Arguments> statusChangedCases() {
        return Stream.of(
                Arguments.of(
                        MatchingStatus.CANCELED,
                        MatchingRequestStatusReason.CONSUMER_CANCELED,
                        MatchingRealtimeEventType.MATCHING_CANCELED,
                        "매칭 요청이 취소되었습니다."
                ),
                Arguments.of(
                        MatchingStatus.NO_AVAILABLE_INSTRUCTOR,
                        MatchingRequestStatusReason.NO_AVAILABLE_INSTRUCTOR,
                        MatchingRealtimeEventType.MATCHING_FAILED,
                        "조건에 맞는 강사를 찾지 못했습니다."
                ),
                Arguments.of(
                        MatchingStatus.FAILED,
                        MatchingRequestStatusReason.SYSTEM_ERROR,
                        MatchingRealtimeEventType.MATCHING_FAILED,
                        "매칭이 종료되었습니다."
                ),
                Arguments.of(
                        MatchingStatus.PAYMENT_EXPIRED,
                        MatchingRequestStatusReason.PAYMENT_TIMEOUT,
                        MatchingRealtimeEventType.MATCHING_FAILED,
                        "매칭이 종료되었습니다."
                ),
                Arguments.of(
                        MatchingStatus.WAITING_FOR_INSTRUCTOR,
                        null,
                        MatchingRealtimeEventType.MATCHING_STATUS_CHANGED,
                        "매칭 상태가 변경되었습니다."
                )
        );
    }
}
