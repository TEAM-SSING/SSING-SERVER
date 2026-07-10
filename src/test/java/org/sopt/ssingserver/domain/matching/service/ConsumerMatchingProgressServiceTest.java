package org.sopt.ssingserver.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.lesson.entity.Lesson;
import org.sopt.ssingserver.domain.lesson.entity.LessonParticipant;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;
import org.sopt.ssingserver.domain.lesson.repository.LessonParticipantRepository;
import org.sopt.ssingserver.domain.lesson.repository.LessonRepository;
import org.sopt.ssingserver.domain.matching.dto.result.ConsumerMatchingConfirmationResult;
import org.sopt.ssingserver.domain.matching.dto.result.ConsumerMatchingPaymentResult;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroup;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroupItem;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestParticipant;
import org.sopt.ssingserver.domain.matching.enums.MatchingConfirmationDecision;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupItemStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.matching.error.MatchingErrorCode;
import org.sopt.ssingserver.domain.matching.event.MatchingConfirmedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingDomainEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingEventPublisher;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferClosedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferClosedReason;
import org.sopt.ssingserver.domain.matching.event.MatchingRequestStatusChangedEvent;
import org.sopt.ssingserver.domain.matching.event.PaymentPendingEvent;
import org.sopt.ssingserver.domain.matching.event.PaymentStatusChangedEvent;
import org.sopt.ssingserver.domain.matching.event.RequesterConfirmationUpdatedEvent;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestGroupItemRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestGroupRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestParticipantRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestRepository;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.Gender;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.payment.entity.MatchingOfferPriceSnapshot;
import org.sopt.ssingserver.domain.payment.entity.MatchingRequestPayment;
import org.sopt.ssingserver.domain.payment.entity.MatchingRequestPriceSnapshot;
import org.sopt.ssingserver.domain.payment.enums.MatchingRequestPaymentStatus;
import org.sopt.ssingserver.domain.payment.repository.MatchingOfferPriceSnapshotRepository;
import org.sopt.ssingserver.domain.payment.repository.MatchingRequestPaymentRepository;
import org.sopt.ssingserver.domain.payment.repository.MatchingRequestPriceSnapshotRepository;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.sopt.ssingserver.global.error.BusinessException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ConsumerMatchingProgressServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-09T01:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private MatchingRequestRepository matchingRequestRepository;

    @Mock
    private MatchingRequestGroupRepository matchingRequestGroupRepository;

    @Mock
    private MatchingRequestGroupItemRepository matchingRequestGroupItemRepository;

    @Mock
    private MatchingOfferPriceSnapshotRepository matchingOfferPriceSnapshotRepository;

    @Mock
    private MatchingRequestPriceSnapshotRepository matchingRequestPriceSnapshotRepository;

    @Mock
    private MatchingRequestPaymentRepository matchingRequestPaymentRepository;

    @Mock
    private MatchingRequestParticipantRepository matchingRequestParticipantRepository;

    @Mock
    private LessonRepository lessonRepository;

    @Mock
    private LessonParticipantRepository lessonParticipantRepository;

    @Mock
    private MatchingEventPublisher matchingEventPublisher;

    @Test
    void respond는_소비자_수락시_요청가격스냅샷과_PENDING_결제를_생성한다() {
        ConsumerMatchingProgressService service = createService();
        MatchingFixture fixture = matchedFixture();
        MatchingOfferPriceSnapshot offerSnapshot = offerPriceSnapshot(70L, fixture.offer(), 80_000);
        givenConfirmableRequest(fixture);
        when(matchingOfferPriceSnapshotRepository.findByMatchingOfferId(50L)).thenReturn(Optional.of(offerSnapshot));
        when(matchingRequestPriceSnapshotRepository.save(any(MatchingRequestPriceSnapshot.class)))
                .thenAnswer(invocation -> {
                    MatchingRequestPriceSnapshot snapshot = invocation.getArgument(0);
                    ReflectionTestUtils.setField(snapshot, "id", 80L);
                    return snapshot;
                });
        when(matchingRequestPaymentRepository.save(any(MatchingRequestPayment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ConsumerMatchingConfirmationResult result = service.respond(
                1L,
                10L,
                MatchingConfirmationDecision.ACCEPTED
        );

        assertThat(result.matchingStatus()).isSameAs(MatchingStatus.PAYMENT_PENDING);
        assertThat(result.itemStatus()).isSameAs(MatchingRequestGroupItemStatus.ACCEPTED);
        assertThat(result.groupStatus()).isSameAs(MatchingRequestGroupStatus.PAYMENT_PENDING);
        assertThat(result.confirmedCount()).isNull();
        assertThat(result.requiredCount()).isNull();
        assertThat(fixture.item().getStatus()).isSameAs(MatchingRequestGroupItemStatus.ACCEPTED);
        assertThat(fixture.group().getStatus()).isSameAs(MatchingRequestGroupStatus.PAYMENT_PENDING);

        ArgumentCaptor<MatchingRequestPriceSnapshot> requestSnapshotCaptor =
                ArgumentCaptor.forClass(MatchingRequestPriceSnapshot.class);
        verify(matchingRequestPriceSnapshotRepository).save(requestSnapshotCaptor.capture());
        MatchingRequestPriceSnapshot requestSnapshot = requestSnapshotCaptor.getValue();
        assertThat(requestSnapshot.getMatchingRequest()).isSameAs(fixture.matchingRequest());
        assertThat(requestSnapshot.getMatchingOfferPriceSnapshot()).isSameAs(offerSnapshot);
        assertThat(requestSnapshot.getHeadcount()).isEqualTo(2);
        assertThat(requestSnapshot.getConsumerPaymentAmount()).isEqualTo(80_000);

        ArgumentCaptor<MatchingRequestPayment> paymentCaptor = ArgumentCaptor.forClass(MatchingRequestPayment.class);
        verify(matchingRequestPaymentRepository).save(paymentCaptor.capture());
        MatchingRequestPayment payment = paymentCaptor.getValue();
        assertThat(payment.getMatchingRequest()).isSameAs(fixture.matchingRequest());
        assertThat(payment.getMatchingRequestPriceSnapshot()).isSameAs(requestSnapshot);
        assertThat(payment.getMatchingOffer()).isSameAs(fixture.offer());
        assertThat(payment.getAmount()).isEqualTo(80_000);
        assertThat(payment.getStatus()).isSameAs(MatchingRequestPaymentStatus.PENDING);
        assertThat(payment.getPaymentRequestedAt()).isEqualTo(FIXED_CLOCK.instant());
        assertThat(payment.getPaymentExpiresAt()).isNull();
        verify(matchingEventPublisher).publish(argThat(event ->
                event instanceof PaymentPendingEvent paymentPendingEvent
                        && paymentPendingEvent.matchingRequestGroupId().equals(20L)
                        && paymentPendingEvent.matchingOfferId().equals(50L)
        ));
    }

    @Test
    void respond는_소비자_거절시_그룹을_닫고_요청을_재탐색_REQUESTED로_되돌린다() {
        ConsumerMatchingProgressService service = createService();
        MatchingFixture fixture = matchedFixture();
        givenConfirmableRequest(fixture);

        ConsumerMatchingConfirmationResult result = service.respond(
                1L,
                10L,
                MatchingConfirmationDecision.REJECTED
        );

        assertThat(result.matchingStatus()).isSameAs(MatchingStatus.REMATCHING);
        assertThat(result.requestStatus()).isSameAs(MatchingRequestStatus.REQUESTED);
        assertThat(result.requestStatusReason()).isSameAs(MatchingRequestStatusReason.CONSUMER_REJECTED_INSTRUCTOR);
        assertThat(result.itemStatus()).isSameAs(MatchingRequestGroupItemStatus.REJECTED);
        assertThat(result.groupStatus()).isSameAs(MatchingRequestGroupStatus.CANCELED);
        assertThat(fixture.matchingRequest().getStatus()).isSameAs(MatchingRequestStatus.REQUESTED);
        assertThat(fixture.matchingRequest().getStatusReason())
                .isSameAs(MatchingRequestStatusReason.CONSUMER_REJECTED_INSTRUCTOR);
        verifyNoInteractions(matchingOfferPriceSnapshotRepository);
        verifyNoInteractions(matchingRequestPriceSnapshotRepository);
        verifyNoInteractions(matchingRequestPaymentRepository);
        verify(matchingEventPublisher, times(2)).publish(any());
        verify(matchingEventPublisher).publish(argThat(event -> event instanceof MatchingOfferClosedEvent));
        verify(matchingEventPublisher).publish(argThat(event ->
                event instanceof MatchingRequestStatusChangedEvent statusEvent
                        && statusEvent.matchingStatus() == MatchingStatus.REMATCHING
        ));
    }

    @Test
    void respond는_다인그룹_거절시_모든_요청에_재탐색_이벤트와_제안종료_이벤트를_발행한다() {
        ConsumerMatchingProgressService service = createService();
        MatchingFixture fixture = matchedFixture();
        MatchingRequest secondRequest = matchingRequest(11L, member(3L, MemberRole.CONSUMER));
        secondRequest.markMatched(fixture.offer());
        MatchingRequestGroupItem secondItem = MatchingRequestGroupItem.createNotRequested(
                secondRequest,
                fixture.group()
        );
        ReflectionTestUtils.setField(secondItem, "id", 31L);
        secondItem.requestConfirmation();
        givenProgressContext(fixture, List.of(fixture.item(), secondItem));

        service.respond(1L, 10L, MatchingConfirmationDecision.REJECTED);

        assertThat(fixture.item().getStatus()).isSameAs(MatchingRequestGroupItemStatus.REJECTED);
        assertThat(secondItem.getStatus()).isSameAs(MatchingRequestGroupItemStatus.CANCELED);
        assertThat(fixture.matchingRequest().getStatus()).isSameAs(MatchingRequestStatus.REQUESTED);
        assertThat(fixture.matchingRequest().getStatusReason())
                .isSameAs(MatchingRequestStatusReason.CONSUMER_REJECTED_INSTRUCTOR);
        assertThat(secondRequest.getStatus()).isSameAs(MatchingRequestStatus.REQUESTED);
        assertThat(secondRequest.getStatusReason())
                .isSameAs(MatchingRequestStatusReason.CONSUMER_REJECTED_INSTRUCTOR);
        assertThat(fixture.group().getStatus()).isSameAs(MatchingRequestGroupStatus.CANCELED);

        ArgumentCaptor<MatchingDomainEvent> eventCaptor = ArgumentCaptor.forClass(MatchingDomainEvent.class);
        verify(matchingEventPublisher, times(3)).publish(eventCaptor.capture());

        List<MatchingRequestStatusChangedEvent> statusEvents = eventCaptor.getAllValues().stream()
                .filter(MatchingRequestStatusChangedEvent.class::isInstance)
                .map(MatchingRequestStatusChangedEvent.class::cast)
                .toList();
        assertThat(statusEvents).hasSize(2);
        assertThat(statusEvents)
                .extracting(MatchingRequestStatusChangedEvent::matchingRequestId)
                .containsExactlyInAnyOrder(10L, 11L);
        assertThat(statusEvents).allSatisfy(event -> {
            assertThat(event.matchingRequestGroupId()).isEqualTo(20L);
            assertThat(event.requestStatus()).isSameAs(MatchingRequestStatus.REQUESTED);
            assertThat(event.requestStatusReason())
                    .isSameAs(MatchingRequestStatusReason.CONSUMER_REJECTED_INSTRUCTOR);
            assertThat(event.matchingStatus()).isSameAs(MatchingStatus.REMATCHING);
        });

        List<MatchingOfferClosedEvent> closedEvents = eventCaptor.getAllValues().stream()
                .filter(MatchingOfferClosedEvent.class::isInstance)
                .map(MatchingOfferClosedEvent.class::cast)
                .toList();
        assertThat(closedEvents).singleElement().satisfies(event -> {
            assertThat(event.matchingRequestGroupId()).isEqualTo(20L);
            assertThat(event.matchingOfferId()).isEqualTo(50L);
            assertThat(event.closedReason()).isSameAs(MatchingOfferClosedReason.GROUP_CANCELED);
        });
    }

    @Test
    void respond는_다인그룹의_일부_수락이면_확정진행_이벤트를_발행한다() {
        ConsumerMatchingProgressService service = createService();
        MatchingFixture fixture = matchedFixture();
        MatchingRequest secondRequest = matchingRequest(11L, member(3L, MemberRole.CONSUMER));
        secondRequest.markMatched(fixture.offer());
        MatchingRequestGroupItem secondItem = MatchingRequestGroupItem.createNotRequested(
                secondRequest,
                fixture.group()
        );
        ReflectionTestUtils.setField(secondItem, "id", 31L);
        secondItem.requestConfirmation();
        givenProgressContext(fixture, List.of(fixture.item(), secondItem));

        ConsumerMatchingConfirmationResult result = service.respond(
                1L,
                10L,
                MatchingConfirmationDecision.ACCEPTED
        );

        assertThat(result.matchingStatus()).isSameAs(MatchingStatus.WAITING_FOR_OTHER_CONFIRMATIONS);
        assertThat(result.confirmedCount()).isEqualTo(1);
        assertThat(result.requiredCount()).isEqualTo(2);
        verify(matchingEventPublisher).publish(argThat(event ->
                event instanceof RequesterConfirmationUpdatedEvent updatedEvent
                        && updatedEvent.acceptedRequesterCount() == 1
                        && updatedEvent.totalRequesterCount() == 2
        ));
        verifyNoInteractions(matchingRequestPaymentRepository);
    }

    @Test
    void completePayment는_마지막_결제완료시_즉시강습_확정시각과_카드기준시각을_저장한다() {
        ConsumerMatchingProgressService service = createService();
        MatchingFixture fixture = paymentPendingFixture();
        MatchingRequestPriceSnapshot requestPriceSnapshot = requestPriceSnapshot(80L, fixture.matchingRequest(), 80_000);
        MatchingRequestPayment payment = pendingPayment(90L, fixture.matchingRequest(), requestPriceSnapshot, fixture.offer());
        MatchingRequestParticipant firstParticipant = participant(100L, fixture.matchingRequest(), 20, Gender.MALE);
        MatchingRequestParticipant secondParticipant = participant(101L, fixture.matchingRequest(), 21, Gender.FEMALE);
        givenPaymentCompletableRequest(fixture, payment);
        when(matchingRequestPaymentRepository.findByMatchingOfferIdForUpdate(50L)).thenReturn(List.of(payment));
        when(matchingRequestParticipantRepository.findByMatchingRequestIdInOrderByMatchingRequestIdAscIdAsc(
                List.of(10L)
        )).thenReturn(List.of(firstParticipant, secondParticipant));
        when(lessonRepository.save(any(Lesson.class))).thenAnswer(invocation -> {
            Lesson lesson = invocation.getArgument(0);
            ReflectionTestUtils.setField(lesson, "id", 110L);
            return lesson;
        });
        when(lessonParticipantRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ConsumerMatchingPaymentResult result = service.completePayment(1L, 10L);

        assertThat(result.matchingStatus()).isSameAs(MatchingStatus.CONFIRMED);
        assertThat(result.paymentStatus()).isSameAs(MatchingRequestPaymentStatus.COMPLETED);
        assertThat(result.groupStatus()).isSameAs(MatchingRequestGroupStatus.CONFIRMED);
        assertThat(result.lessonId()).isEqualTo(110L);
        assertThat(payment.getPaidAt()).isEqualTo(FIXED_CLOCK.instant());
        assertThat(fixture.group().getStatus()).isSameAs(MatchingRequestGroupStatus.CONFIRMED);
        assertThat(fixture.matchingRequest().getStatus()).isSameAs(MatchingRequestStatus.CONFIRMED);

        ArgumentCaptor<Lesson> lessonCaptor = ArgumentCaptor.forClass(Lesson.class);
        verify(lessonRepository).save(lessonCaptor.capture());
        Lesson lesson = lessonCaptor.getValue();
        assertThat(lesson.getInstructorProfile()).isSameAs(fixture.offer().getInstructorProfile());
        assertThat(lesson.getResort()).isSameAs(fixture.matchingRequest().getResort());
        assertThat(lesson.getMatchingOffer()).isSameAs(fixture.offer());
        assertThat(lesson.getSport()).isSameAs(Sport.SNOWBOARD);
        assertThat(lesson.getLessonLevel()).isSameAs(LessonLevel.FIRST_TIME);
        assertThat(lesson.getTotalHeadcount()).isEqualTo(2);
        assertThat(lesson.getDurationMinutes()).isEqualTo(120);
        assertThat(lesson.getConfirmedAt()).isEqualTo(FIXED_CLOCK.instant());
        assertThat(lesson.getScheduledAt()).isEqualTo(FIXED_CLOCK.instant());
        assertThat(lesson.getStatus()).isSameAs(LessonStatus.CONFIRMED);
        verify(matchingEventPublisher).publish(argThat(event ->
                event instanceof MatchingConfirmedEvent confirmedEvent
                        && confirmedEvent.lessonId().equals(110L)
                        && confirmedEvent.matchingOfferId().equals(50L)
        ));

        ArgumentCaptor<Iterable<LessonParticipant>> lessonParticipantsCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(lessonParticipantRepository).saveAll(lessonParticipantsCaptor.capture());
        List<LessonParticipant> lessonParticipants = new ArrayList<>();
        lessonParticipantsCaptor.getValue().forEach(lessonParticipants::add);
        assertThat(lessonParticipants).hasSize(2);
        assertThat(lessonParticipants)
                .extracting(LessonParticipant::getMatchingRequestParticipant)
                .containsExactly(firstParticipant, secondParticipant);
    }

    @Test
    void completePayment는_다인그룹의_일부_결제이면_결제진행_이벤트를_발행한다() {
        ConsumerMatchingProgressService service = createService();
        MatchingFixture fixture = paymentPendingFixture();
        MatchingRequestPriceSnapshot firstSnapshot = requestPriceSnapshot(80L, fixture.matchingRequest(), 40_000);
        MatchingRequestPayment firstPayment = pendingPayment(
                90L,
                fixture.matchingRequest(),
                firstSnapshot,
                fixture.offer()
        );
        MatchingRequest secondRequest = matchingRequest(11L, member(3L, MemberRole.CONSUMER));
        secondRequest.markMatched(fixture.offer());
        MatchingRequestGroupItem secondItem = MatchingRequestGroupItem.createNotRequested(
                secondRequest,
                fixture.group()
        );
        ReflectionTestUtils.setField(secondItem, "id", 31L);
        secondItem.requestConfirmation();
        secondItem.accept(FIXED_CLOCK.instant().minusSeconds(30));
        MatchingRequestPriceSnapshot secondSnapshot = requestPriceSnapshot(81L, secondRequest, 40_000);
        MatchingRequestPayment secondPayment = pendingPayment(91L, secondRequest, secondSnapshot, fixture.offer());
        givenProgressContext(fixture, List.of(fixture.item(), secondItem));
        when(matchingRequestPaymentRepository.findFirstByMatchingRequestIdAndStatusOrderByIdDesc(
                10L,
                MatchingRequestPaymentStatus.PENDING
        )).thenReturn(Optional.of(firstPayment));
        when(matchingRequestPaymentRepository.findByMatchingOfferIdForUpdate(50L))
                .thenReturn(List.of(firstPayment, secondPayment));

        ConsumerMatchingPaymentResult result = service.completePayment(1L, 10L);

        assertThat(result.matchingStatus()).isSameAs(MatchingStatus.WAITING_FOR_OTHER_PAYMENTS);
        assertThat(result.paidCount()).isEqualTo(1);
        assertThat(result.requiredCount()).isEqualTo(2);
        verify(matchingEventPublisher).publish(argThat(event ->
                event instanceof PaymentStatusChangedEvent changedEvent
                        && changedEvent.paidRequesterCount() == 1
                        && changedEvent.totalRequesterCount() == 2
        ));
        verifyNoInteractions(lessonRepository, lessonParticipantRepository);
    }

    @Test
    void completePayment는_PENDING_결제가_없으면_MATCHING_PAYMENT_NOT_PENDING을_던진다() {
        ConsumerMatchingProgressService service = createService();
        MatchingFixture fixture = paymentPendingFixture();
        givenPaymentCompletableRequest(fixture, null);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.completePayment(1L, 10L))
                .satisfies(exception -> assertThat(exception.getErrorCode())
                        .isSameAs(MatchingErrorCode.MATCHING_PAYMENT_NOT_PENDING));

        verifyNoInteractions(lessonRepository);
        verifyNoInteractions(lessonParticipantRepository);
    }

    @Test
    void respond는_이미_닫힌_그룹이면_MATCHING_GROUP_ALREADY_CLOSED를_던진다() {
        ConsumerMatchingProgressService service = createService();
        MatchingFixture fixture = matchedFixture();
        fixture.group().cancel();
        givenProgressContext(fixture);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.respond(1L, 10L, MatchingConfirmationDecision.ACCEPTED))
                .satisfies(exception -> assertThat(exception.getErrorCode())
                        .isSameAs(MatchingErrorCode.MATCHING_GROUP_ALREADY_CLOSED));

        verifyNoInteractions(matchingOfferPriceSnapshotRepository);
        verifyNoInteractions(matchingRequestPriceSnapshotRepository);
        verifyNoInteractions(matchingRequestPaymentRepository);
    }

    @Test
    void completePayment는_이미_닫힌_그룹이면_MATCHING_GROUP_ALREADY_CLOSED를_던진다() {
        ConsumerMatchingProgressService service = createService();
        MatchingFixture fixture = paymentPendingFixture();
        fixture.group().confirm();
        givenProgressContext(fixture);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.completePayment(1L, 10L))
                .satisfies(exception -> assertThat(exception.getErrorCode())
                        .isSameAs(MatchingErrorCode.MATCHING_GROUP_ALREADY_CLOSED));

        verifyNoInteractions(matchingRequestPaymentRepository);
        verifyNoInteractions(lessonRepository);
        verifyNoInteractions(lessonParticipantRepository);
    }

    private ConsumerMatchingProgressService createService() {
        return new ConsumerMatchingProgressService(
                matchingRequestRepository,
                matchingRequestGroupRepository,
                matchingRequestGroupItemRepository,
                matchingOfferPriceSnapshotRepository,
                matchingRequestPriceSnapshotRepository,
                matchingRequestPaymentRepository,
                matchingRequestParticipantRepository,
                lessonRepository,
                lessonParticipantRepository,
                matchingEventPublisher,
                new MatchingAfterCommitExecutor(),
                FIXED_CLOCK
        );
    }

    private void givenConfirmableRequest(MatchingFixture fixture) {
        givenProgressContext(fixture);
    }

    private void givenPaymentCompletableRequest(
            MatchingFixture fixture,
            MatchingRequestPayment payment
    ) {
        givenProgressContext(fixture);
        when(matchingRequestPaymentRepository.findFirstByMatchingRequestIdAndStatusOrderByIdDesc(
                10L,
                MatchingRequestPaymentStatus.PENDING
        )).thenReturn(Optional.ofNullable(payment));
    }

    private void givenProgressContext(MatchingFixture fixture) {
        givenProgressContext(fixture, List.of(fixture.item()));
    }

    private void givenProgressContext(
            MatchingFixture fixture,
            List<MatchingRequestGroupItem> groupItems
    ) {
        when(matchingRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(fixture.matchingRequest()));
        when(matchingRequestGroupItemRepository.findFirstByMatchingRequestIdOrderByIdDesc(10L))
                .thenReturn(Optional.of(fixture.item()));
        when(matchingRequestGroupRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(fixture.group()));
        when(matchingRequestGroupItemRepository.findByMatchingRequestGroupIdForUpdate(20L))
                .thenReturn(groupItems);
    }

    private MatchingFixture matchedFixture() {
        Member consumer = member(1L, MemberRole.CONSUMER);
        MatchingRequest matchingRequest = matchingRequest(10L, consumer);
        MatchingRequestGroup group = MatchingRequestGroup.createCandidate(120);
        ReflectionTestUtils.setField(group, "id", 20L);
        group.expose();
        MatchingRequestGroupItem item = MatchingRequestGroupItem.createNotRequested(matchingRequest, group);
        ReflectionTestUtils.setField(item, "id", 30L);
        InstructorProfile instructorProfile = instructorProfile(40L, member(2L, MemberRole.INSTRUCTOR));
        MatchingOffer offer = MatchingOffer.create(instructorProfile, group, FIXED_CLOCK.instant());
        ReflectionTestUtils.setField(offer, "id", 50L);

        offer.accept(FIXED_CLOCK.instant().minusSeconds(60));
        group.markInstructorAccepted();
        item.requestConfirmation();
        matchingRequest.markMatched(offer);

        return new MatchingFixture(matchingRequest, group, item, offer);
    }

    private MatchingFixture paymentPendingFixture() {
        MatchingFixture fixture = matchedFixture();
        fixture.item().accept(FIXED_CLOCK.instant().minusSeconds(30));
        fixture.group().markConsumerAccepted();
        fixture.group().markPaymentPending();
        return fixture;
    }

    private MatchingRequest matchingRequest(
            Long id,
            Member member
    ) {
        MatchingRequest matchingRequest = MatchingRequest.createUnlimitedSearch(
                member,
                resort(),
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                2,
                List.of(120),
                true
        );
        ReflectionTestUtils.setField(matchingRequest, "id", id);
        return matchingRequest;
    }

    private MatchingOfferPriceSnapshot offerPriceSnapshot(
            Long id,
            MatchingOffer matchingOffer,
            int consumerTotalAmount
    ) {
        MatchingOfferPriceSnapshot snapshot = construct(MatchingOfferPriceSnapshot.class);
        ReflectionTestUtils.setField(snapshot, "id", id);
        ReflectionTestUtils.setField(snapshot, "matchingOffer", matchingOffer);
        ReflectionTestUtils.setField(snapshot, "consumerTotalAmount", consumerTotalAmount);
        ReflectionTestUtils.setField(snapshot, "totalHeadcount", 2);
        return snapshot;
    }

    private MatchingRequestPriceSnapshot requestPriceSnapshot(
            Long id,
            MatchingRequest matchingRequest,
            int consumerPaymentAmount
    ) {
        MatchingRequestPriceSnapshot snapshot = construct(MatchingRequestPriceSnapshot.class);
        ReflectionTestUtils.setField(snapshot, "id", id);
        ReflectionTestUtils.setField(snapshot, "matchingRequest", matchingRequest);
        ReflectionTestUtils.setField(snapshot, "headcount", matchingRequest.getHeadcount());
        ReflectionTestUtils.setField(snapshot, "consumerPaymentAmount", consumerPaymentAmount);
        return snapshot;
    }

    private MatchingRequestPayment pendingPayment(
            Long id,
            MatchingRequest matchingRequest,
            MatchingRequestPriceSnapshot requestPriceSnapshot,
            MatchingOffer matchingOffer
    ) {
        MatchingRequestPayment payment = MatchingRequestPayment.createPending(
                matchingRequest,
                requestPriceSnapshot,
                matchingOffer,
                requestPriceSnapshot.getConsumerPaymentAmount(),
                FIXED_CLOCK.instant().minusSeconds(30),
                null
        );
        ReflectionTestUtils.setField(payment, "id", id);
        return payment;
    }

    private MatchingRequestParticipant participant(
            Long id,
            MatchingRequest matchingRequest,
            int age,
            Gender gender
    ) {
        MatchingRequestParticipant participant = MatchingRequestParticipant.create(matchingRequest, age, gender);
        ReflectionTestUtils.setField(participant, "id", id);
        return participant;
    }

    private InstructorProfile instructorProfile(
            Long id,
            Member member
    ) {
        InstructorProfile instructorProfile = InstructorProfile.create(
                member,
                "김강사",
                "010-1234-5678",
                Gender.FEMALE,
                LocalDate.of(1998, 3, 1),
                "친절한 강사입니다.",
                LocalDate.of(2020, 1, 1),
                InstructorApprovalStatus.APPROVED,
                FIXED_CLOCK.instant()
        );
        ReflectionTestUtils.setField(instructorProfile, "id", id);
        return instructorProfile;
    }

    private Member member(
            Long id,
            MemberRole role
    ) {
        Member member = Member.create("테스트", null, role, MemberStatus.ACTIVE);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private Resort resort() {
        Resort resort = construct(Resort.class);
        ReflectionTestUtils.setField(resort, "code", "HIGH1");
        ReflectionTestUtils.setField(resort, "name", "하이원리조트");
        ReflectionTestUtils.setField(resort, "displayName", "하이원");
        return resort;
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

    private record MatchingFixture(
            MatchingRequest matchingRequest,
            MatchingRequestGroup group,
            MatchingRequestGroupItem item,
            MatchingOffer offer
    ) {
    }
}
