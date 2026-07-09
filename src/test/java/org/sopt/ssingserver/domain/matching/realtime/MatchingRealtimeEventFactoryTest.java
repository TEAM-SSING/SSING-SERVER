package org.sopt.ssingserver.domain.matching.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent.LessonSummary;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent.InstructorAcceptedPayload;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent.MatchingConfirmedPayload;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent.MatchingOfferClosedPayload;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent.MatchingOfferReceivedPayload;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent.MatchingStatusPayload;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent.PaymentPendingPayload;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent.PaymentStatusChangedPayload;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent.RequesterConfirmationUpdatedPayload;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent.RequestSummary;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEventType;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeRecipientRole;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupItemStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.matching.event.InstructorAcceptedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingConfirmedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferCanceledEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferClosedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferClosedReason;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferCreatedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingRequestStatusChangedEvent;
import org.sopt.ssingserver.domain.matching.event.PaymentPendingEvent;
import org.sopt.ssingserver.domain.matching.event.PaymentStatusChangedEvent;
import org.sopt.ssingserver.domain.matching.event.RequesterConfirmationUpdatedEvent;
import org.sopt.ssingserver.domain.matching.realtime.MatchingNotificationContextLoader.ConsumerNotificationContext;
import org.sopt.ssingserver.domain.matching.realtime.MatchingNotificationContextLoader.InstructorNotificationContext;
import org.sopt.ssingserver.domain.matching.realtime.MatchingNotificationContextLoader.MatchingGroupNotificationContext;
import org.sopt.ssingserver.domain.matching.realtime.MatchingNotificationContextLoader.MatchingOfferRecipientContext;
import org.sopt.ssingserver.domain.matching.realtime.MatchingNotificationContextLoader.MatchingPaymentNotificationContext;
import org.sopt.ssingserver.domain.matching.realtime.MatchingNotificationContextLoader.PaymentRecipientContext;
import org.sopt.ssingserver.domain.matching.realtime.MatchingNotificationContextLoader.MatchingStatusChangedContext;
import org.sopt.ssingserver.domain.payment.enums.MatchingRequestPaymentStatus;

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
        when(contextLoader.load(event)).thenReturn(Optional.of(new MatchingGroupNotificationContext(
                new InstructorNotificationContext(99L, 40L, "김강사", null),
                List.of(new ConsumerNotificationContext(
                        88L,
                        10L,
                        MatchingRequestGroupItemStatus.NOT_REQUESTED
                )),
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

        List<MatchingRealtimeDelivery> result = factory.create(event);

        assertThat(result).hasSize(2);
        MatchingRealtimeDelivery delivery = result.stream()
                .filter(item -> item.event().recipientRole() == MatchingRealtimeRecipientRole.INSTRUCTOR)
                .findFirst()
                .orElseThrow();
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

        MatchingRealtimeEvent consumerEvent = result.stream()
                .map(MatchingRealtimeDelivery::event)
                .filter(item -> item.recipientRole() == MatchingRealtimeRecipientRole.CONSUMER)
                .findFirst()
                .orElseThrow();
        assertThat(consumerEvent.matchingRequestId()).isEqualTo(10L);
        assertThat(consumerEvent.groupId()).isEqualTo(20L);
        assertThat(consumerEvent.matchingStatus()).isSameAs(MatchingStatus.WAITING_FOR_INSTRUCTOR);
    }

    @Test
    void create는_강사수락을_그룹_소비자의_INSTRUCTOR_ACCEPTED로_변환한다() {
        InstructorAcceptedEvent event = new InstructorAcceptedEvent(
                UUID.randomUUID(),
                Instant.parse("2026-07-07T00:00:00Z"),
                20L,
                30L
        );
        when(contextLoader.load(event)).thenReturn(Optional.of(groupContext(
                List.of(new ConsumerNotificationContext(88L, 10L, MatchingRequestGroupItemStatus.PENDING))
        )));

        List<MatchingRealtimeDelivery> result = factory.create(event);

        assertThat(result).hasSize(1);
        MatchingRealtimeEvent realtimeEvent = result.getFirst().event();
        assertThat(realtimeEvent.eventType()).isSameAs(MatchingRealtimeEventType.INSTRUCTOR_ACCEPTED);
        assertThat(realtimeEvent.matchingRequestId()).isEqualTo(10L);
        assertThat(realtimeEvent.groupId()).isEqualTo(20L);
        assertThat(realtimeEvent.matchingStatus()).isSameAs(MatchingStatus.WAITING_FOR_CONFIRMATION);
        InstructorAcceptedPayload payload = (InstructorAcceptedPayload) realtimeEvent.payload();
        assertThat(payload.instructor().instructorProfileId()).isEqualTo(40L);
        assertThat(payload.lessonSummary().durationMinutes()).isEqualTo(120);
    }

    @Test
    void create는_소비자확인_진행률과_수신자별_matchingStatus를_변환한다() {
        RequesterConfirmationUpdatedEvent event = new RequesterConfirmationUpdatedEvent(
                UUID.randomUUID(),
                Instant.parse("2026-07-07T00:00:00Z"),
                20L,
                30L,
                1,
                2
        );
        when(contextLoader.load(event)).thenReturn(Optional.of(groupContext(List.of(
                new ConsumerNotificationContext(88L, 10L, MatchingRequestGroupItemStatus.ACCEPTED),
                new ConsumerNotificationContext(89L, 11L, MatchingRequestGroupItemStatus.PENDING)
        ))));

        List<MatchingRealtimeDelivery> result = factory.create(event);

        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(
                        MatchingRealtimeDelivery::recipientMemberId,
                        delivery -> delivery.event().matchingRequestId(),
                        delivery -> delivery.event().matchingStatus()
                )
                .containsExactlyInAnyOrder(
                        tuple(88L, 10L, MatchingStatus.WAITING_FOR_OTHER_CONFIRMATIONS),
                        tuple(89L, 11L, MatchingStatus.WAITING_FOR_CONFIRMATION)
                );
        assertThat(result).allSatisfy(delivery -> {
            RequesterConfirmationUpdatedPayload payload =
                    (RequesterConfirmationUpdatedPayload) delivery.event().payload();
            assertThat(payload.progressSummary().acceptedRequesterCount()).isEqualTo(1);
            assertThat(payload.progressSummary().totalRequesterCount()).isEqualTo(2);
        });
    }

    @Test
    void create는_결제대기_이벤트에_요청별_paymentId를_담는다() {
        PaymentPendingEvent event = new PaymentPendingEvent(
                UUID.randomUUID(),
                Instant.parse("2026-07-07T00:00:00Z"),
                20L,
                30L
        );
        MatchingPaymentNotificationContext context = paymentContext(List.of(
                new PaymentRecipientContext(88L, 10L, 70L, MatchingRequestPaymentStatus.PENDING)
        ));
        when(contextLoader.load(event)).thenReturn(Optional.of(context));

        List<MatchingRealtimeDelivery> result = factory.create(event);

        assertThat(result).hasSize(1);
        MatchingRealtimeEvent realtimeEvent = result.getFirst().event();
        assertThat(realtimeEvent.eventType()).isSameAs(MatchingRealtimeEventType.PAYMENT_PENDING);
        assertThat(realtimeEvent.matchingStatus()).isSameAs(MatchingStatus.PAYMENT_PENDING);
        PaymentPendingPayload payload = (PaymentPendingPayload) realtimeEvent.payload();
        assertThat(payload.matchingRequestPaymentId()).isEqualTo(70L);
    }

    @Test
    void create는_결제진행률과_수신자별_matchingStatus를_변환한다() {
        PaymentStatusChangedEvent event = new PaymentStatusChangedEvent(
                UUID.randomUUID(),
                Instant.parse("2026-07-07T00:00:00Z"),
                20L,
                30L,
                1,
                2
        );
        when(contextLoader.load(event)).thenReturn(Optional.of(paymentContext(List.of(
                new PaymentRecipientContext(88L, 10L, 70L, MatchingRequestPaymentStatus.COMPLETED),
                new PaymentRecipientContext(89L, 11L, 71L, MatchingRequestPaymentStatus.PENDING)
        ))));

        List<MatchingRealtimeDelivery> result = factory.create(event);

        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(
                        MatchingRealtimeDelivery::recipientMemberId,
                        delivery -> delivery.event().matchingRequestId(),
                        delivery -> delivery.event().matchingStatus()
                )
                .containsExactlyInAnyOrder(
                        tuple(88L, 10L, MatchingStatus.WAITING_FOR_OTHER_PAYMENTS),
                        tuple(89L, 11L, MatchingStatus.PAYMENT_PENDING)
                );
        assertThat(result).allSatisfy(delivery -> {
            PaymentStatusChangedPayload payload = (PaymentStatusChangedPayload) delivery.event().payload();
            assertThat(payload.progressSummary().paidRequesterCount()).isEqualTo(1);
            assertThat(payload.progressSummary().totalRequesterCount()).isEqualTo(2);
        });
    }

    @Test
    void create는_최종확정을_소비자와_강사에게_각각_전송한다() {
        MatchingConfirmedEvent event = new MatchingConfirmedEvent(
                UUID.randomUUID(),
                Instant.parse("2026-07-07T00:00:00Z"),
                20L,
                30L,
                100L
        );
        when(contextLoader.load(event)).thenReturn(Optional.of(groupContext(
                List.of(new ConsumerNotificationContext(88L, 10L, MatchingRequestGroupItemStatus.ACCEPTED))
        )));

        List<MatchingRealtimeDelivery> result = factory.create(event);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(delivery -> delivery.event().recipientRole())
                .containsExactlyInAnyOrder(
                        MatchingRealtimeRecipientRole.CONSUMER,
                        MatchingRealtimeRecipientRole.INSTRUCTOR
                );
        assertThat(result).allSatisfy(delivery -> {
            MatchingConfirmedPayload payload = (MatchingConfirmedPayload) delivery.event().payload();
            assertThat(payload.lessonId()).isEqualTo(100L);
        });
        MatchingRealtimeEvent consumerEvent = result.stream()
                .map(MatchingRealtimeDelivery::event)
                .filter(item -> item.recipientRole() == MatchingRealtimeRecipientRole.CONSUMER)
                .findFirst()
                .orElseThrow();
        MatchingRealtimeEvent instructorEvent = result.stream()
                .map(MatchingRealtimeDelivery::event)
                .filter(item -> item.recipientRole() == MatchingRealtimeRecipientRole.INSTRUCTOR)
                .findFirst()
                .orElseThrow();
        assertThat(consumerEvent.matchingStatus()).isSameAs(MatchingStatus.CONFIRMED);
        assertThat(instructorEvent.matchingStatus()).isNull();
    }

    @Test
    void create는_강사제안_종료사유를_MATCHING_OFFER_CLOSED로_변환한다() {
        MatchingOfferClosedEvent event = new MatchingOfferClosedEvent(
                UUID.randomUUID(),
                Instant.parse("2026-07-07T00:00:00Z"),
                20L,
                30L,
                MatchingOfferClosedReason.REJECTED
        );
        when(contextLoader.load(event)).thenReturn(Optional.of(new MatchingOfferRecipientContext(99L)));

        List<MatchingRealtimeDelivery> result = factory.create(event);

        assertThat(result).hasSize(1);
        MatchingRealtimeEvent realtimeEvent = result.getFirst().event();
        assertThat(realtimeEvent.eventType()).isSameAs(MatchingRealtimeEventType.MATCHING_OFFER_CLOSED);
        assertThat(realtimeEvent.recipientRole()).isSameAs(MatchingRealtimeRecipientRole.INSTRUCTOR);
        MatchingOfferClosedPayload payload = (MatchingOfferClosedPayload) realtimeEvent.payload();
        assertThat(payload.closedReason()).isEqualTo("REJECTED");
    }

    @Test
    void create는_소비자_완전취소를_강사의_MATCHING_CANCELED로_변환한다() {
        MatchingOfferCanceledEvent event = new MatchingOfferCanceledEvent(
                UUID.randomUUID(),
                Instant.parse("2026-07-07T00:00:00Z"),
                20L,
                30L,
                MatchingRequestStatusReason.CONSUMER_CANCELED
        );
        when(contextLoader.load(event)).thenReturn(Optional.of(new MatchingOfferRecipientContext(99L)));

        List<MatchingRealtimeDelivery> result = factory.create(event);

        assertThat(result).hasSize(1);
        MatchingRealtimeEvent realtimeEvent = result.getFirst().event();
        assertThat(realtimeEvent.eventType()).isSameAs(MatchingRealtimeEventType.MATCHING_CANCELED);
        assertThat(realtimeEvent.offerId()).isEqualTo(30L);
        assertThat(realtimeEvent.groupId()).isEqualTo(20L);
        assertThat(realtimeEvent.recipientRole()).isSameAs(MatchingRealtimeRecipientRole.INSTRUCTOR);
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

        List<MatchingRealtimeDelivery> result = factory.create(event);

        assertThat(result).hasSize(1);
        MatchingRealtimeEvent realtimeEvent = result.getFirst().event();
        assertThat(result.getFirst().recipientMemberId()).isEqualTo(88L);
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

        List<MatchingRealtimeDelivery> result = factory.create(event);

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

    private MatchingGroupNotificationContext groupContext(List<ConsumerNotificationContext> consumers) {
        return new MatchingGroupNotificationContext(
                new InstructorNotificationContext(99L, 40L, "김강사", null),
                consumers,
                "홍길동",
                3,
                consumers.size(),
                "하이원",
                "SNOWBOARD",
                "FIRST_TIME",
                120,
                4,
                "IMMEDIATE"
        );
    }

    private MatchingPaymentNotificationContext paymentContext(List<PaymentRecipientContext> recipients) {
        return new MatchingPaymentNotificationContext(groupContext(List.of()), recipients);
    }
}
