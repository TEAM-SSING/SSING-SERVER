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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.instructor.repository.InstructorProfileRepository;
import org.sopt.ssingserver.domain.matching.dto.result.InstructorMatchingOfferDecisionResult;
import org.sopt.ssingserver.domain.matching.dto.result.InstructorMatchingOfferDetailResult;
import org.sopt.ssingserver.domain.matching.dto.result.InstructorMatchingOffersResult;
import org.sopt.ssingserver.domain.matching.dto.result.NextMatchingOfferResult;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroup;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroupItem;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestParticipant;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferDecision;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupItemStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.matching.error.MatchingErrorCode;
import org.sopt.ssingserver.domain.matching.event.InstructorAcceptedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingEventPublisher;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferClosedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferClosedReason;
import org.sopt.ssingserver.domain.matching.event.MatchingRequestStatusChangedEvent;
import org.sopt.ssingserver.domain.matching.repository.MatchingOfferRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestGroupItemRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestGroupRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestParticipantRepository;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.Gender;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.payment.entity.MatchingOfferPriceSnapshot;
import org.sopt.ssingserver.domain.payment.repository.MatchingOfferPriceSnapshotRepository;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.sopt.ssingserver.global.error.BusinessException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InstructorMatchingOfferServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-07T00:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private InstructorProfileRepository instructorProfileRepository;

    @Mock
    private MatchingOfferRepository matchingOfferRepository;

    @Mock
    private MatchingRequestGroupRepository matchingRequestGroupRepository;

    @Mock
    private MatchingRequestGroupItemRepository matchingRequestGroupItemRepository;

    @Mock
    private MatchingRequestParticipantRepository matchingRequestParticipantRepository;

    @Mock
    private MatchingOfferPriceSnapshotRepository matchingOfferPriceSnapshotRepository;

    @Mock
    private MatchingSearchService matchingSearchService;

    @Mock
    private MatchingEventPublisher matchingEventPublisher;

    @Test
    void respond는_강사_수락시_제안과_그룹과_요청을_최종확인_대기로_전환한다() {
        InstructorMatchingOfferService service = createService();
        InstructorProfile instructorProfile = instructorProfile(10L, member(1L, MemberRole.INSTRUCTOR));
        MatchingRequestGroup group = exposedGroup(20L);
        MatchingRequest matchingRequest = matchingRequest(30L, member(2L, MemberRole.CONSUMER));
        matchingRequest.markGrouped();
        MatchingRequestGroupItem item = item(40L, matchingRequest, group);
        MatchingOffer offer = offeredOffer(50L, instructorProfile, group);
        givenRespondableOffer(instructorProfile, offer, group, List.of(item));

        InstructorMatchingOfferDecisionResult result = service.respond(
                1L,
                50L,
                MatchingOfferDecision.ACCEPTED
        );

        assertThat(result.offerId()).isEqualTo(50L);
        assertThat(result.offerStatus()).isSameAs(MatchingOfferStatus.ACCEPTED);
        assertThat(result.groupStatus()).isSameAs(MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED);
        assertThat(result.requesterConfirmationExpiresAt()).isNull();
        assertThat(offer.getStatus()).isSameAs(MatchingOfferStatus.ACCEPTED);
        assertThat(offer.getRespondedAt()).isEqualTo(FIXED_CLOCK.instant());
        assertThat(group.getStatus()).isSameAs(MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED);
        assertThat(item.getStatus()).isSameAs(MatchingRequestGroupItemStatus.PENDING);
        assertThat(matchingRequest.getStatus()).isSameAs(MatchingRequestStatus.MATCHED);
        assertThat(matchingRequest.getMatchingOffer()).isSameAs(offer);
        assertThat(matchingRequest.getExpiresAt()).isNull();
        verifyNoInteractions(matchingSearchService);
        verify(matchingEventPublisher).publish(argThat(event ->
                event instanceof InstructorAcceptedEvent acceptedEvent
                        && acceptedEvent.matchingRequestGroupId().equals(20L)
                        && acceptedEvent.matchingOfferId().equals(50L)
        ));
    }

    @Test
    void respond는_강사_거절시_다음_후보가_있으면_그룹을_EXPOSED로_유지한다() {
        InstructorMatchingOfferService service = createService();
        InstructorProfile instructorProfile = instructorProfile(10L, member(1L, MemberRole.INSTRUCTOR));
        MatchingRequestGroup group = exposedGroup(20L);
        MatchingRequest matchingRequest = matchingRequest(30L, member(2L, MemberRole.CONSUMER));
        matchingRequest.markGrouped();
        MatchingRequestGroupItem item = item(40L, matchingRequest, group);
        MatchingOffer offer = offeredOffer(50L, instructorProfile, group);
        MatchingOffer nextOffer = offeredOffer(51L, instructorProfile(11L, member(3L, MemberRole.INSTRUCTOR)), group);
        givenRespondableOffer(instructorProfile, offer, group, List.of(item));
        when(matchingSearchService.ensureNextOfferForGroup(matchingRequest, group, FIXED_CLOCK.instant()))
                .thenReturn(NextMatchingOfferResult.created(nextOffer));

        InstructorMatchingOfferDecisionResult result = service.respond(
                1L,
                50L,
                MatchingOfferDecision.REJECTED
        );

        assertThat(result.offerStatus()).isSameAs(MatchingOfferStatus.REJECTED);
        assertThat(result.groupStatus()).isSameAs(MatchingRequestGroupStatus.EXPOSED);
        assertThat(offer.getStatus()).isSameAs(MatchingOfferStatus.REJECTED);
        assertThat(group.getStatus()).isSameAs(MatchingRequestGroupStatus.EXPOSED);
        assertThat(matchingRequest.getStatus()).isSameAs(MatchingRequestStatus.GROUPED);
        verify(matchingEventPublisher).publish(argThat(event ->
                event instanceof MatchingOfferClosedEvent closedEvent
                        && closedEvent.matchingOfferId().equals(50L)
                        && closedEvent.closedReason() == MatchingOfferClosedReason.REJECTED
        ));
    }

    @Test
    void respond는_강사_거절시_다른_활성_제안이_이미_있으면_그룹을_EXPOSED로_유지한다() {
        InstructorMatchingOfferService service = createService();
        InstructorProfile instructorProfile = instructorProfile(10L, member(1L, MemberRole.INSTRUCTOR));
        MatchingRequestGroup group = exposedGroup(20L);
        MatchingRequest matchingRequest = matchingRequest(30L, member(2L, MemberRole.CONSUMER));
        matchingRequest.markGrouped();
        MatchingRequestGroupItem item = item(40L, matchingRequest, group);
        MatchingOffer offer = offeredOffer(50L, instructorProfile, group);
        MatchingOffer activeOffer = offeredOffer(51L, instructorProfile(11L, member(3L, MemberRole.INSTRUCTOR)), group);
        givenRespondableOffer(instructorProfile, offer, group, List.of(item));
        when(matchingSearchService.ensureNextOfferForGroup(matchingRequest, group, FIXED_CLOCK.instant()))
                .thenReturn(NextMatchingOfferResult.alreadyActive(activeOffer));

        InstructorMatchingOfferDecisionResult result = service.respond(
                1L,
                50L,
                MatchingOfferDecision.REJECTED
        );

        assertThat(result.offerStatus()).isSameAs(MatchingOfferStatus.REJECTED);
        assertThat(result.groupStatus()).isSameAs(MatchingRequestGroupStatus.EXPOSED);
        assertThat(offer.getStatus()).isSameAs(MatchingOfferStatus.REJECTED);
        assertThat(activeOffer.getStatus()).isSameAs(MatchingOfferStatus.OFFERED);
        assertThat(group.getStatus()).isSameAs(MatchingRequestGroupStatus.EXPOSED);
        assertThat(matchingRequest.getStatus()).isSameAs(MatchingRequestStatus.GROUPED);
        assertThat(matchingRequest.getStatusReason()).isNull();
    }

    @Test
    void respond는_강사_거절시_다음_후보가_없으면_그룹을_닫고_요청을_REMATCHING용_REQUESTED로_되돌린다() {
        InstructorMatchingOfferService service = createService();
        InstructorProfile instructorProfile = instructorProfile(10L, member(1L, MemberRole.INSTRUCTOR));
        MatchingRequestGroup group = exposedGroup(20L);
        MatchingRequest matchingRequest = matchingRequest(30L, member(2L, MemberRole.CONSUMER));
        matchingRequest.markGrouped();
        MatchingRequestGroupItem item = item(40L, matchingRequest, group);
        MatchingOffer offer = offeredOffer(50L, instructorProfile, group);
        givenRespondableOffer(instructorProfile, offer, group, List.of(item));
        when(matchingSearchService.ensureNextOfferForGroup(matchingRequest, group, FIXED_CLOCK.instant()))
                .thenReturn(NextMatchingOfferResult.noCandidate());

        InstructorMatchingOfferDecisionResult result = service.respond(
                1L,
                50L,
                MatchingOfferDecision.REJECTED
        );

        assertThat(result.offerStatus()).isSameAs(MatchingOfferStatus.REJECTED);
        assertThat(result.groupStatus()).isSameAs(MatchingRequestGroupStatus.CANCELED);
        assertThat(group.getStatus()).isSameAs(MatchingRequestGroupStatus.CANCELED);
        assertThat(matchingRequest.getStatus()).isSameAs(MatchingRequestStatus.REQUESTED);
        assertThat(matchingRequest.getStatusReason()).isSameAs(MatchingRequestStatusReason.INSTRUCTOR_REJECTED);
        verify(matchingEventPublisher, times(2)).publish(any());
        verify(matchingEventPublisher).publish(argThat(event ->
                event instanceof MatchingRequestStatusChangedEvent statusEvent
                        && statusEvent.matchingRequestId().equals(30L)
                        && statusEvent.matchingStatus() == org.sopt.ssingserver.domain.matching.enums.MatchingStatus.REMATCHING
        ));
    }

    @Test
    void respond는_제안_소유_강사가_아니면_MATCHING_OFFER_NOT_FOUND를_던진다() {
        InstructorMatchingOfferService service = createService();
        InstructorProfile currentInstructor = instructorProfile(10L, member(1L, MemberRole.INSTRUCTOR));
        InstructorProfile otherInstructor = instructorProfile(11L, member(3L, MemberRole.INSTRUCTOR));
        MatchingOffer offer = offeredOffer(50L, otherInstructor, exposedGroup(20L));
        when(instructorProfileRepository.findByMemberId(1L)).thenReturn(Optional.of(currentInstructor));
        when(matchingOfferRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(offer));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.respond(1L, 50L, MatchingOfferDecision.ACCEPTED))
                .satisfies(exception -> assertThat(exception.getErrorCode())
                        .isSameAs(MatchingErrorCode.MATCHING_OFFER_NOT_FOUND));

        verifyNoInteractions(matchingRequestGroupRepository);
        verifyNoInteractions(matchingRequestGroupItemRepository);
        verifyNoInteractions(matchingSearchService);
    }

    @Test
    void respond는_이미_응답한_제안이면_MATCHING_OFFER_ALREADY_RESPONDED를_던지고_주변상태를_변경하지_않는다() {
        InstructorMatchingOfferService service = createService();
        InstructorProfile instructorProfile = instructorProfile(10L, member(1L, MemberRole.INSTRUCTOR));
        MatchingRequestGroup group = exposedGroup(20L);
        MatchingOffer offer = offeredOffer(50L, instructorProfile, group);
        offer.reject(FIXED_CLOCK.instant().minusSeconds(30));
        givenOfferAndGroup(instructorProfile, offer, group);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.respond(1L, 50L, MatchingOfferDecision.ACCEPTED))
                .satisfies(exception -> assertThat(exception.getErrorCode())
                        .isSameAs(MatchingErrorCode.MATCHING_OFFER_ALREADY_RESPONDED));

        assertThat(offer.getStatus()).isSameAs(MatchingOfferStatus.REJECTED);
        assertThat(group.getStatus()).isSameAs(MatchingRequestGroupStatus.EXPOSED);
        verifyNoInteractions(matchingRequestGroupItemRepository);
        verifyNoInteractions(matchingSearchService);
    }

    @Test
    void respond는_이미_닫힌_그룹이면_MATCHING_GROUP_ALREADY_CLOSED를_던지고_제안을_변경하지_않는다() {
        InstructorMatchingOfferService service = createService();
        InstructorProfile instructorProfile = instructorProfile(10L, member(1L, MemberRole.INSTRUCTOR));
        MatchingRequestGroup group = exposedGroup(20L);
        group.cancel();
        MatchingOffer offer = offeredOffer(50L, instructorProfile, group);
        givenOfferAndGroup(instructorProfile, offer, group);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.respond(1L, 50L, MatchingOfferDecision.ACCEPTED))
                .satisfies(exception -> assertThat(exception.getErrorCode())
                        .isSameAs(MatchingErrorCode.MATCHING_GROUP_ALREADY_CLOSED));

        assertThat(offer.getStatus()).isSameAs(MatchingOfferStatus.OFFERED);
        assertThat(group.getStatus()).isSameAs(MatchingRequestGroupStatus.CANCELED);
        verifyNoInteractions(matchingRequestGroupItemRepository);
        verifyNoInteractions(matchingSearchService);
    }

    @Test
    void getCurrentOffers는_현재_노출중인_OFFERED_제안만_조회하고_그룹아이템을_한번에_조회한다() {
        InstructorMatchingOfferService service = createService();
        InstructorProfile instructorProfile = instructorProfile(10L, member(1L, MemberRole.INSTRUCTOR));
        MatchingRequestGroup firstGroup = exposedGroup(20L);
        MatchingRequestGroup secondGroup = exposedGroup(21L);
        MatchingRequest firstRequest = matchingRequest(30L, member(2L, MemberRole.CONSUMER));
        MatchingRequest secondRequest = matchingRequest(31L, member(3L, MemberRole.CONSUMER));
        MatchingRequestGroupItem firstItem = item(40L, firstRequest, firstGroup);
        MatchingRequestGroupItem secondItem = item(41L, secondRequest, secondGroup);
        MatchingOffer firstOffer = offeredOffer(50L, instructorProfile, firstGroup);
        MatchingOffer secondOffer = offeredOffer(51L, instructorProfile, secondGroup);
        MatchingOfferPriceSnapshot firstPriceSnapshot = offerPriceSnapshot(firstOffer, 80_000, 20_000);
        MatchingOfferPriceSnapshot secondPriceSnapshot = offerPriceSnapshot(secondOffer, 90_000, 30_000);
        when(instructorProfileRepository.findByMemberId(1L)).thenReturn(Optional.of(instructorProfile));
        when(matchingOfferRepository.findByInstructorProfileIdAndStatusOrderByIdAsc(
                10L,
                MatchingOfferStatus.OFFERED,
                PageRequest.of(0, 20)
        )).thenReturn(new PageImpl<>(List.of(firstOffer, secondOffer), PageRequest.of(0, 20), 2));
        when(matchingRequestGroupItemRepository.findByMatchingRequestGroupIdInOrderByGroupIdAscItemIdAsc(
                List.of(20L, 21L)
        )).thenReturn(List.of(firstItem, secondItem));
        when(matchingOfferPriceSnapshotRepository.findByMatchingOfferIdIn(List.of(50L, 51L)))
                .thenReturn(List.of(firstPriceSnapshot, secondPriceSnapshot));

        InstructorMatchingOffersResult result = service.getCurrentOffers(1L, 0, 20);

        assertThat(result.items()).hasSize(2);
        InstructorMatchingOffersResult.ItemResult firstItemResult = result.items().getFirst();
        assertThat(firstItemResult.offerId()).isEqualTo(50L);
        assertThat(firstItemResult.groupId()).isEqualTo(20L);
        assertThat(firstItemResult.offerStatus()).isSameAs(MatchingOfferStatus.OFFERED);
        assertThat(firstItemResult.expiresAt()).isNull();
        assertThat(firstItemResult.requestSummary().requesterName()).isEqualTo("테스트");
        assertThat(firstItemResult.requestSummary().headcount()).isEqualTo(2);
        assertThat(firstItemResult.requestSummary().matchingRequestCount()).isEqualTo(1);
        assertThat(firstItemResult.lessonSummary().resort().code()).isEqualTo("HIGH1");
        assertThat(firstItemResult.lessonSummary().resort().displayName()).isEqualTo("하이원");
        assertThat(firstItemResult.lessonSummary().sport()).isSameAs(Sport.SNOWBOARD);
        assertThat(firstItemResult.lessonSummary().level()).isSameAs(LessonLevel.FIRST_TIME);
        assertThat(firstItemResult.lessonSummary().durationMinutes()).isEqualTo(120);
        assertThat(firstItemResult.lessonSummary().totalHeadcount()).isEqualTo(2);
        assertThat(firstItemResult.lessonSummary().startType()).isEqualTo("IMMEDIATE");
        assertThat(firstItemResult.priceSummary().lessonPriceAmount()).isEqualTo(80_000);
        assertThat(firstItemResult.priceSummary().resortPassFeeAmount()).isEqualTo(20_000);
        assertThat(firstItemResult.priceSummary().totalPaymentAmount()).isEqualTo(100_000);
        assertThat(result.items().get(1).offerId()).isEqualTo(51L);
        assertThat(result.items().get(1).groupId()).isEqualTo(21L);
        assertThat(result.items().get(1).priceSummary().totalPaymentAmount()).isEqualTo(120_000);
        assertThat(result.currentPage()).isZero();
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.hasNext()).isFalse();
        verify(matchingRequestGroupItemRepository).findByMatchingRequestGroupIdInOrderByGroupIdAscItemIdAsc(
                List.of(20L, 21L)
        );
        verify(matchingOfferPriceSnapshotRepository).findByMatchingOfferIdIn(List.of(50L, 51L));
    }

    @Test
    void getOfferDetail은_본인에게_노출된_OFFERED_제안으로_I07_화면을_복구한다() {
        InstructorMatchingOfferService service = createService();
        InstructorProfile instructorProfile = instructorProfile(10L, member(1L, MemberRole.INSTRUCTOR));
        MatchingRequestGroup group = exposedGroup(20L);
        MatchingRequest firstRequest = matchingRequest(30L, member(2L, MemberRole.CONSUMER));
        MatchingRequest secondRequest = matchingRequest(31L, member(3L, MemberRole.CONSUMER));
        MatchingRequestGroupItem firstGroupItem = item(40L, firstRequest, group);
        MatchingRequestGroupItem secondGroupItem = item(41L, secondRequest, group);
        MatchingOffer offer = offeredOffer(50L, instructorProfile, group);
        MatchingOfferPriceSnapshot priceSnapshot = offerPriceSnapshot(offer, 80_000, 20_000);
        List<MatchingRequestParticipant> participants = List.of(
                participant(firstRequest, 10, Gender.MALE),
                participant(firstRequest, 10, Gender.MALE),
                participant(secondRequest, 12, Gender.FEMALE),
                participant(secondRequest, 14, Gender.MALE)
        );
        when(instructorProfileRepository.findByMemberId(1L)).thenReturn(Optional.of(instructorProfile));
        when(matchingOfferRepository.findDetailById(50L)).thenReturn(Optional.of(offer));
        when(matchingRequestGroupItemRepository.findByMatchingRequestGroupIdOrderByIdAsc(20L))
                .thenReturn(List.of(firstGroupItem, secondGroupItem));
        when(matchingRequestParticipantRepository.findByMatchingRequestIdInOrderByMatchingRequestIdAscIdAsc(
                List.of(30L, 31L)
        )).thenReturn(participants);
        when(matchingOfferPriceSnapshotRepository.findByMatchingOfferId(50L)).thenReturn(Optional.of(priceSnapshot));

        InstructorMatchingOfferDetailResult result = service.getOfferDetail(1L, 50L);

        assertThat(result.offerId()).isEqualTo(50L);
        assertThat(result.groupId()).isEqualTo(20L);
        assertThat(result.offerStatus()).isSameAs(MatchingOfferStatus.OFFERED);
        assertThat(result.groupStatus()).isSameAs(MatchingRequestGroupStatus.EXPOSED);
        assertThat(result.matchingStatus()).isSameAs(MatchingStatus.WAITING_FOR_INSTRUCTOR);
        assertThat(result.requestSummary().requesterName()).isEqualTo("테스트");
        assertThat(result.lessonSummary().level()).isSameAs(LessonLevel.FIRST_TIME);
        assertThat(result.lessonSummary().durationMinutes()).isEqualTo(120);
        assertThat(result.lessonSummary().totalHeadcount()).isEqualTo(4);
        assertThat(result.priceSummary().totalPaymentAmount()).isEqualTo(100_000);
        assertThat(result.participants())
                .extracting(
                        InstructorMatchingOfferDetailResult.ParticipantResult::age,
                        InstructorMatchingOfferDetailResult.ParticipantResult::gender
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(10, Gender.MALE),
                        org.assertj.core.groups.Tuple.tuple(10, Gender.MALE),
                        org.assertj.core.groups.Tuple.tuple(12, Gender.FEMALE),
                        org.assertj.core.groups.Tuple.tuple(14, Gender.MALE)
                );
        verify(matchingRequestParticipantRepository)
                .findByMatchingRequestIdInOrderByMatchingRequestIdAscIdAsc(List.of(30L, 31L));
    }

    @Test
    void getOfferDetail은_수락후_소비자최종확인대기_협상으로_I08_화면을_복구한다() {
        InstructorMatchingOfferService service = createService();
        InstructorProfile instructorProfile = instructorProfile(10L, member(1L, MemberRole.INSTRUCTOR));
        MatchingRequestGroup group = exposedGroup(20L);
        group.markInstructorAccepted();
        MatchingRequest matchingRequest = matchingRequest(30L, member(2L, MemberRole.CONSUMER));
        MatchingRequestGroupItem groupItem = item(40L, matchingRequest, group);
        MatchingOffer offer = offeredOffer(50L, instructorProfile, group);
        offer.accept(FIXED_CLOCK.instant());
        MatchingOfferPriceSnapshot priceSnapshot = offerPriceSnapshot(offer, 80_000, 20_000);
        when(instructorProfileRepository.findByMemberId(1L)).thenReturn(Optional.of(instructorProfile));
        when(matchingOfferRepository.findDetailById(50L)).thenReturn(Optional.of(offer));
        when(matchingRequestGroupItemRepository.findByMatchingRequestGroupIdOrderByIdAsc(20L))
                .thenReturn(List.of(groupItem));
        when(matchingOfferPriceSnapshotRepository.findByMatchingOfferId(50L)).thenReturn(Optional.of(priceSnapshot));

        InstructorMatchingOfferDetailResult result = service.getOfferDetail(1L, 50L);

        assertThat(result.offerStatus()).isSameAs(MatchingOfferStatus.ACCEPTED);
        assertThat(result.groupStatus()).isSameAs(MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED);
        assertThat(result.matchingStatus()).isSameAs(MatchingStatus.WAITING_FOR_CONFIRMATION);
    }

    @Test
    void getOfferDetail은_수락후_결제대기_협상도_복구한다() {
        InstructorMatchingOfferService service = createService();
        InstructorProfile instructorProfile = instructorProfile(10L, member(1L, MemberRole.INSTRUCTOR));
        MatchingRequestGroup group = exposedGroup(20L);
        group.markInstructorAccepted();
        group.markPaymentPending();
        MatchingRequest matchingRequest = matchingRequest(30L, member(2L, MemberRole.CONSUMER));
        MatchingRequestGroupItem groupItem = item(40L, matchingRequest, group);
        MatchingOffer offer = offeredOffer(50L, instructorProfile, group);
        offer.accept(FIXED_CLOCK.instant());
        MatchingOfferPriceSnapshot priceSnapshot = offerPriceSnapshot(offer, 80_000, 20_000);
        when(instructorProfileRepository.findByMemberId(1L)).thenReturn(Optional.of(instructorProfile));
        when(matchingOfferRepository.findDetailById(50L)).thenReturn(Optional.of(offer));
        when(matchingRequestGroupItemRepository.findByMatchingRequestGroupIdOrderByIdAsc(20L))
                .thenReturn(List.of(groupItem));
        when(matchingOfferPriceSnapshotRepository.findByMatchingOfferId(50L)).thenReturn(Optional.of(priceSnapshot));

        InstructorMatchingOfferDetailResult result = service.getOfferDetail(1L, 50L);

        assertThat(result.groupStatus()).isSameAs(MatchingRequestGroupStatus.PAYMENT_PENDING);
        assertThat(result.matchingStatus()).isSameAs(MatchingStatus.PAYMENT_PENDING);
    }

    @Test
    void getOfferDetail은_CONSUMER_ACCEPTED_내부상태를_복구응답으로_노출하지_않는다() {
        InstructorMatchingOfferService service = createService();
        InstructorProfile instructorProfile = instructorProfile(10L, member(1L, MemberRole.INSTRUCTOR));
        MatchingRequestGroup group = exposedGroup(20L);
        group.markInstructorAccepted();
        group.markConsumerAccepted();
        MatchingOffer offer = offeredOffer(50L, instructorProfile, group);
        offer.accept(FIXED_CLOCK.instant());
        when(instructorProfileRepository.findByMemberId(1L)).thenReturn(Optional.of(instructorProfile));
        when(matchingOfferRepository.findDetailById(50L)).thenReturn(Optional.of(offer));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.getOfferDetail(1L, 50L))
                .satisfies(exception -> assertThat(exception.getErrorCode())
                        .isSameAs(MatchingErrorCode.MATCHING_OFFER_NOT_FOUND));
        verifyNoInteractions(matchingRequestGroupItemRepository, matchingRequestParticipantRepository);
    }

    @Test
    void getOfferDetail은_다른강사_종료_확정된_제안을_MATCHING_OFFER_NOT_FOUND로_처리한다() {
        InstructorMatchingOfferService service = createService();
        InstructorProfile currentInstructor = instructorProfile(10L, member(1L, MemberRole.INSTRUCTOR));
        InstructorProfile otherInstructor = instructorProfile(11L, member(3L, MemberRole.INSTRUCTOR));
        MatchingOffer otherOffer = offeredOffer(50L, otherInstructor, exposedGroup(20L));
        when(instructorProfileRepository.findByMemberId(1L)).thenReturn(Optional.of(currentInstructor));
        when(matchingOfferRepository.findDetailById(50L)).thenReturn(Optional.of(otherOffer));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.getOfferDetail(1L, 50L))
                .satisfies(exception -> assertThat(exception.getErrorCode())
                        .isSameAs(MatchingErrorCode.MATCHING_OFFER_NOT_FOUND));

        MatchingRequestGroup confirmedGroup = exposedGroup(21L);
        confirmedGroup.markInstructorAccepted();
        confirmedGroup.markPaymentPending();
        confirmedGroup.confirm();
        MatchingOffer confirmedOffer = offeredOffer(51L, currentInstructor, confirmedGroup);
        confirmedOffer.accept(FIXED_CLOCK.instant());
        when(matchingOfferRepository.findDetailById(51L)).thenReturn(Optional.of(confirmedOffer));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.getOfferDetail(1L, 51L))
                .satisfies(exception -> assertThat(exception.getErrorCode())
                        .isSameAs(MatchingErrorCode.MATCHING_OFFER_NOT_FOUND));
        verifyNoInteractions(matchingRequestGroupItemRepository, matchingRequestParticipantRepository);
    }

    private InstructorMatchingOfferService createService() {
        return new InstructorMatchingOfferService(
                instructorProfileRepository,
                matchingOfferRepository,
                matchingRequestGroupRepository,
                matchingRequestGroupItemRepository,
                matchingRequestParticipantRepository,
                matchingOfferPriceSnapshotRepository,
                matchingSearchService,
                new MatchingTimeoutPolicy(),
                new MatchingEventDispatcher(matchingEventPublisher, new MatchingAfterCommitExecutor()),
                FIXED_CLOCK
        );
    }

    private void givenRespondableOffer(
            InstructorProfile instructorProfile,
            MatchingOffer offer,
            MatchingRequestGroup group,
            List<MatchingRequestGroupItem> groupItems
    ) {
        when(instructorProfileRepository.findByMemberId(1L)).thenReturn(Optional.of(instructorProfile));
        when(matchingOfferRepository.findByIdForUpdate(offer.getId())).thenReturn(Optional.of(offer));
        when(matchingRequestGroupRepository.findByIdForUpdate(group.getId())).thenReturn(Optional.of(group));
        when(matchingRequestGroupItemRepository.findByMatchingRequestGroupIdForUpdate(group.getId()))
                .thenReturn(groupItems);
    }

    private void givenOfferAndGroup(
            InstructorProfile instructorProfile,
            MatchingOffer offer,
            MatchingRequestGroup group
    ) {
        when(instructorProfileRepository.findByMemberId(1L)).thenReturn(Optional.of(instructorProfile));
        when(matchingOfferRepository.findByIdForUpdate(offer.getId())).thenReturn(Optional.of(offer));
        when(matchingRequestGroupRepository.findByIdForUpdate(group.getId())).thenReturn(Optional.of(group));
    }

    private MatchingOffer offeredOffer(
            Long id,
            InstructorProfile instructorProfile,
            MatchingRequestGroup group
    ) {
        MatchingOffer offer = MatchingOffer.create(
                instructorProfile,
                group,
                FIXED_CLOCK.instant()
        );
        ReflectionTestUtils.setField(offer, "id", id);
        return offer;
    }

    private MatchingRequestGroup exposedGroup(Long id) {
        MatchingRequestGroup group = MatchingRequestGroup.createCandidate(120);
        group.expose();
        ReflectionTestUtils.setField(group, "id", id);
        return group;
    }

    private MatchingOfferPriceSnapshot offerPriceSnapshot(
            MatchingOffer matchingOffer,
            int lessonPriceAmount,
            int resortPassFeeAmount
    ) {
        MatchingOfferPriceSnapshot snapshot = construct(MatchingOfferPriceSnapshot.class);
        ReflectionTestUtils.setField(snapshot, "matchingOffer", matchingOffer);
        ReflectionTestUtils.setField(snapshot, "consumerTotalAmount", lessonPriceAmount);
        ReflectionTestUtils.setField(snapshot, "resortPassFeeAmount", resortPassFeeAmount);
        ReflectionTestUtils.setField(
                snapshot,
                "totalPaymentAmount",
                lessonPriceAmount + resortPassFeeAmount
        );
        return snapshot;
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

    private MatchingRequestParticipant participant(
            MatchingRequest matchingRequest,
            int age,
            Gender gender
    ) {
        return MatchingRequestParticipant.create(matchingRequest, age, gender);
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
                List.of(120, 180),
                true
        );
        ReflectionTestUtils.setField(matchingRequest, "id", id);
        return matchingRequest;
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
}
