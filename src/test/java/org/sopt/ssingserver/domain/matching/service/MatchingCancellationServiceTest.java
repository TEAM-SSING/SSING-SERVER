package org.sopt.ssingserver.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingCancellationResult;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroup;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroupItem;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupItemStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.matching.error.MatchingErrorCode;
import org.sopt.ssingserver.domain.matching.event.MatchingEventPublisher;
import org.sopt.ssingserver.domain.matching.event.MatchingDomainEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferCanceledEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingRequestStatusChangedEvent;
import org.sopt.ssingserver.domain.matching.repository.MatchingOfferRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestGroupItemRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestRepository;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.payment.entity.MatchingRequestPayment;
import org.sopt.ssingserver.domain.payment.enums.MatchingRequestPaymentStatus;
import org.sopt.ssingserver.domain.payment.repository.MatchingRequestPaymentRepository;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MatchingCancellationServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-07T00:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private MatchingRequestRepository matchingRequestRepository;

    @Mock
    private MatchingRequestGroupItemRepository matchingRequestGroupItemRepository;

    @Mock
    private MatchingOfferRepository matchingOfferRepository;

    @Mock
    private MatchingRequestPaymentRepository matchingRequestPaymentRepository;

    @Mock
    private MatchingStatusResolver matchingStatusResolver;

    @Mock
    private MatchingEventPublisher matchingEventPublisher;

    @Test
    void cancel은_소비자요청과_활성그룹_제안_결제를_취소한다() {
        MatchingCancellationService service = createService();
        MatchingRequest matchingRequest = matchingRequest(1L, member(10L));
        matchingRequest.markGrouped();
        MatchingRequestGroup group = group(20L);
        MatchingRequestGroupItem item = item(21L, matchingRequest, group);
        MatchingOffer offer = offer(30L, group);
        MatchingRequestPayment payment = payment(40L, matchingRequest, MatchingRequestPaymentStatus.PENDING);

        when(matchingRequestRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(matchingRequest));
        when(matchingRequestGroupItemRepository.findFirstByMatchingRequestIdOrderByIdDesc(1L))
                .thenReturn(Optional.of(item));
        when(matchingOfferRepository.findByMatchingRequestGroupIdAndStatusIn(eq(20L), any()))
                .thenReturn(List.of(offer));
        when(matchingRequestPaymentRepository.findFirstByMatchingRequestIdOrderByIdDesc(1L))
                .thenReturn(Optional.of(payment));
        when(matchingStatusResolver.resolve(any(), any(), any(), any(), any()))
                .thenReturn(MatchingStatus.CANCELED);

        Logger logger = (Logger) LoggerFactory.getLogger(MatchingCancellationService.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);

        try {
            MatchingCancellationResult result = service.cancel(10L, 1L);

            assertThat(result.matchingRequestId()).isEqualTo(1L);
            assertThat(result.matchingStatus()).isSameAs(MatchingStatus.CANCELED);
            assertThat(result.requestStatus()).isSameAs(MatchingRequestStatus.CANCELED);
            assertThat(result.requestStatusReason()).isSameAs(MatchingRequestStatusReason.CONSUMER_CANCELED);
            assertThat(result.canceledAt()).isEqualTo(FIXED_CLOCK.instant());
            assertThat(matchingRequest.getStatus()).isSameAs(MatchingRequestStatus.CANCELED);
            assertThat(matchingRequest.getStatusReason()).isSameAs(MatchingRequestStatusReason.CONSUMER_CANCELED);
            assertThat(matchingRequest.getCanceledAt()).isEqualTo(FIXED_CLOCK.instant());
            assertThat(group.getStatus()).isSameAs(MatchingRequestGroupStatus.CANCELED);
            assertThat(item.getStatus()).isSameAs(MatchingRequestGroupItemStatus.CANCELED);
            assertThat(offer.getStatus()).isSameAs(MatchingOfferStatus.CANCELED);
            assertThat(payment.getStatus()).isSameAs(MatchingRequestPaymentStatus.CANCELED);
            assertThat(payment.getCanceledAt()).isEqualTo(FIXED_CLOCK.instant());

            ArgumentCaptor<MatchingDomainEvent> eventCaptor = ArgumentCaptor.forClass(MatchingDomainEvent.class);
            verify(matchingEventPublisher, org.mockito.Mockito.times(2)).publish(eventCaptor.capture());
            MatchingRequestStatusChangedEvent statusEvent = eventCaptor.getAllValues().stream()
                    .filter(MatchingRequestStatusChangedEvent.class::isInstance)
                    .map(MatchingRequestStatusChangedEvent.class::cast)
                    .findFirst()
                    .orElseThrow();
            assertThat(statusEvent.matchingRequestId()).isEqualTo(1L);
            assertThat(statusEvent.requestStatus()).isSameAs(MatchingRequestStatus.CANCELED);
            assertThat(statusEvent.requestStatusReason())
                    .isSameAs(MatchingRequestStatusReason.CONSUMER_CANCELED);
            assertThat(statusEvent.matchingStatus()).isSameAs(MatchingStatus.CANCELED);
            assertThat(statusEvent.occurredAt()).isEqualTo(FIXED_CLOCK.instant());
            assertThat(eventCaptor.getAllValues())
                    .anyMatch(event -> event instanceof MatchingOfferCanceledEvent canceledEvent
                            && canceledEvent.matchingOfferId().equals(30L));

            ILoggingEvent logEvent = appender.list.getFirst();
            assertThat(logEvent.getLevel()).isSameAs(Level.INFO);
            assertThat(logEvent.getFormattedMessage()).isEqualTo("Matching request canceled");
            assertThat(keyValueMap(logEvent))
                    .containsEntry("event", "matching.request.cancel.success")
                    .containsEntry("matching_request_id", "1")
                    .containsEntry("member_id", "10")
                    .containsEntry("matching_status", "CANCELED")
                    .containsEntry("request_status", "CANCELED")
                    .containsEntry("canceled_offer_count", 1)
                    .containsEntry("payment_canceled", true);
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
        }
    }

    @Test
    void cancel은_요청_소유자가_아니면_FORBIDDEN을_던지고_주변상태를_변경하지_않는다() {
        MatchingCancellationService service = createService();
        MatchingRequest matchingRequest = matchingRequest(1L, member(10L));
        when(matchingRequestRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(matchingRequest));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.cancel(11L, 1L))
                .satisfies(exception -> assertThat(exception.getErrorCode()).isSameAs(CommonErrorCode.FORBIDDEN));

        assertThat(matchingRequest.getStatus()).isSameAs(MatchingRequestStatus.REQUESTED);
        verifyNoInteractions(matchingRequestGroupItemRepository);
        verifyNoInteractions(matchingOfferRepository);
        verifyNoInteractions(matchingRequestPaymentRepository);
        verifyNoInteractions(matchingStatusResolver);
        verify(matchingEventPublisher, never()).publish(any());
    }

    @Test
    void cancel은_없는_요청이면_MATCHING_REQUEST_NOT_FOUND를_던진다() {
        MatchingCancellationService service = createService();
        when(matchingRequestRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.cancel(10L, 1L))
                .satisfies(exception -> assertThat(exception.getErrorCode())
                        .isSameAs(MatchingErrorCode.MATCHING_REQUEST_NOT_FOUND));

        verifyNoInteractions(matchingRequestGroupItemRepository);
        verifyNoInteractions(matchingOfferRepository);
        verifyNoInteractions(matchingRequestPaymentRepository);
        verifyNoInteractions(matchingStatusResolver);
        verify(matchingEventPublisher, never()).publish(any());
    }

    @Test
    void cancel은_이미_취소된_요청이면_MATCHING_CANCEL_NOT_ALLOWED를_던진다() {
        MatchingCancellationService service = createService();
        MatchingRequest matchingRequest = matchingRequest(1L, member(10L));
        matchingRequest.cancelByConsumer(FIXED_CLOCK.instant().minusSeconds(60));
        when(matchingRequestRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(matchingRequest));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.cancel(10L, 1L))
                .satisfies(exception -> assertThat(exception.getErrorCode())
                        .isSameAs(MatchingErrorCode.MATCHING_CANCEL_NOT_ALLOWED));

        assertThat(matchingRequest.getCanceledAt()).isEqualTo(FIXED_CLOCK.instant().minusSeconds(60));
        verifyNoInteractions(matchingRequestGroupItemRepository);
        verifyNoInteractions(matchingOfferRepository);
        verifyNoInteractions(matchingRequestPaymentRepository);
        verifyNoInteractions(matchingStatusResolver);
        verify(matchingEventPublisher, never()).publish(any());
    }

    @Test
    void cancel은_결제완료_요청이면_MVP_환불제외로_MATCHING_CANCEL_NOT_ALLOWED를_던진다() {
        MatchingCancellationService service = createService();
        MatchingRequest matchingRequest = matchingRequest(1L, member(10L));
        MatchingRequestGroup group = group(20L);
        MatchingRequestGroupItem item = item(21L, matchingRequest, group);
        MatchingOffer offer = offer(30L, group);
        offer.accept(FIXED_CLOCK.instant().minusSeconds(30));
        matchingRequest.markMatched(offer);
        MatchingRequestPayment payment = payment(40L, matchingRequest, MatchingRequestPaymentStatus.COMPLETED);

        when(matchingRequestRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(matchingRequest));
        when(matchingRequestGroupItemRepository.findFirstByMatchingRequestIdOrderByIdDesc(1L))
                .thenReturn(Optional.of(item));
        when(matchingRequestPaymentRepository.findFirstByMatchingRequestIdOrderByIdDesc(1L))
                .thenReturn(Optional.of(payment));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.cancel(10L, 1L))
                .satisfies(exception -> assertThat(exception.getErrorCode())
                        .isSameAs(MatchingErrorCode.MATCHING_CANCEL_NOT_ALLOWED));

        assertThat(matchingRequest.getStatus()).isSameAs(MatchingRequestStatus.MATCHED);
        assertThat(group.getStatus()).isSameAs(MatchingRequestGroupStatus.EXPOSED);
        assertThat(item.getStatus()).isSameAs(MatchingRequestGroupItemStatus.NOT_REQUESTED);
        assertThat(offer.getStatus()).isSameAs(MatchingOfferStatus.ACCEPTED);
        assertThat(payment.getStatus()).isSameAs(MatchingRequestPaymentStatus.COMPLETED);
        verifyNoInteractions(matchingStatusResolver);
        verify(matchingOfferRepository, never()).findByMatchingRequestGroupIdAndStatusIn(any(), any());
        verify(matchingEventPublisher, never()).publish(any());
    }

    private MatchingCancellationService createService() {
        return new MatchingCancellationService(
                matchingRequestRepository,
                matchingRequestGroupItemRepository,
                matchingOfferRepository,
                matchingRequestPaymentRepository,
                matchingStatusResolver,
                matchingEventPublisher,
                new MatchingAfterCommitExecutor(),
                FIXED_CLOCK
        );
    }

    private MatchingRequest matchingRequest(
            Long id,
            Member member
    ) {
        MatchingRequest matchingRequest = MatchingRequest.create(
                member,
                resort(),
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                2,
                List.of(120, 180),
                true
        );
        ReflectionTestUtils.setField(matchingRequest, "id", id);
        return matchingRequest;
    }

    private Member member(Long id) {
        Member member = Member.create("소비자", null, MemberRole.CONSUMER, MemberStatus.ACTIVE);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private MatchingRequestGroup group(Long id) {
        MatchingRequestGroup group = MatchingRequestGroup.createCandidate(120);
        group.expose();
        ReflectionTestUtils.setField(group, "id", id);
        return group;
    }

    private MatchingRequestGroupItem item(
            Long id,
            MatchingRequest matchingRequest,
            MatchingRequestGroup group
    ) {
        MatchingRequestGroupItem item = MatchingRequestGroupItem.createNotRequested(matchingRequest, group);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private MatchingOffer offer(
            Long id,
            MatchingRequestGroup group
    ) {
        MatchingOffer offer = MatchingOffer.create(instructorProfile(100L), group, FIXED_CLOCK.instant());
        ReflectionTestUtils.setField(offer, "id", id);
        return offer;
    }

    private MatchingRequestPayment payment(
            Long id,
            MatchingRequest matchingRequest,
            MatchingRequestPaymentStatus status
    ) {
        MatchingRequestPayment payment = construct(MatchingRequestPayment.class);
        ReflectionTestUtils.setField(payment, "id", id);
        ReflectionTestUtils.setField(payment, "matchingRequest", matchingRequest);
        ReflectionTestUtils.setField(payment, "status", status);
        ReflectionTestUtils.setField(payment, "amount", 100_000);
        ReflectionTestUtils.setField(payment, "paymentRequestedAt", FIXED_CLOCK.instant().minusSeconds(60));
        ReflectionTestUtils.setField(payment, "paymentExpiresAt", FIXED_CLOCK.instant().plusSeconds(60));
        return payment;
    }

    private InstructorProfile instructorProfile(Long id) {
        InstructorProfile instructorProfile = construct(InstructorProfile.class);
        ReflectionTestUtils.setField(instructorProfile, "id", id);
        return instructorProfile;
    }

    private Resort resort() {
        Resort resort = construct(Resort.class);
        ReflectionTestUtils.setField(resort, "code", "HIGH1");
        ReflectionTestUtils.setField(resort, "name", "하이원리조트");
        ReflectionTestUtils.setField(resort, "displayName", "하이원");
        return resort;
    }

    private Map<String, Object> keyValueMap(ILoggingEvent event) {
        return event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(keyValuePair -> keyValuePair.key, keyValuePair -> keyValuePair.value));
    }

    private <T> T construct(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
