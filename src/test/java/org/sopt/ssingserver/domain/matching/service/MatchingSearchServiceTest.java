package org.sopt.ssingserver.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.never;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sopt.ssingserver.domain.instructor.entity.InstructorMatchingSetting;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.instructor.repository.InstructorMatchingSettingRepository;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingSearchResult;
import org.sopt.ssingserver.domain.matching.dto.result.NextMatchingOfferResult;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroup;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroupItem;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.matching.event.MatchingEventPublisher;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferCreatedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingRequestStatusChangedEvent;
import org.sopt.ssingserver.domain.matching.repository.MatchingOfferRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestGroupItemRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestGroupRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestRepository;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.Gender;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class MatchingSearchServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-07T00:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private MatchingRequestRepository matchingRequestRepository;

    @Mock
    private MatchingRequestGroupRepository matchingRequestGroupRepository;

    @Mock
    private MatchingRequestGroupItemRepository matchingRequestGroupItemRepository;

    @Mock
    private MatchingOfferRepository matchingOfferRepository;

    @Mock
    private InstructorMatchingSettingRepository instructorMatchingSettingRepository;

    @Mock
    private MatchingEventPublisher matchingEventPublisher;

    @Test
    void search는_무제한탐색_요청에_후보가_없어도_REQUESTED_SEARCHING을_유지한다() {
        MatchingSearchService service = createService();
        MatchingRequest matchingRequest = matchingRequest(1L, 2, List.of(120, 180), null);
        when(matchingRequestRepository.findByIdAndStatusForUpdate(1L, MatchingRequestStatus.REQUESTED))
                .thenReturn(Optional.of(matchingRequest));
        when(instructorMatchingSettingRepository.findExposedCandidates(
                matchingRequest.getResort(),
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                2,
                matchingRequest.getRequestedDurationMinutes(),
                true
        )).thenReturn(List.of());

        MatchingSearchResult result = service.search(1L);

        assertThat(result.matchingStatus()).isSameAs(MatchingStatus.SEARCHING);
        assertThat(result.requestStatus()).isSameAs(MatchingRequestStatus.REQUESTED);
        assertThat(result.requestStatusReason()).isNull();
        verifyNoInteractions(matchingRequestGroupRepository);
        verify(matchingRequestGroupItemRepository, never()).save(any());
        verifyNoInteractions(matchingOfferRepository);
        verify(matchingEventPublisher, never()).publish(any());
    }

    @Test
    void search는_만료된_REQUESTED_요청을_NO_AVAILABLE_INSTRUCTOR로_최종_실패시킨다() {
        MatchingSearchService service = createService();
        MatchingRequest matchingRequest = matchingRequest(1L, 2, List.of(120, 180), Instant.parse("2026-07-06T23:59:59Z"));
        when(matchingRequestRepository.findByIdAndStatusForUpdate(1L, MatchingRequestStatus.REQUESTED))
                .thenReturn(Optional.of(matchingRequest));

        MatchingSearchResult result = service.search(1L);

        assertThat(matchingRequest.getStatus()).isSameAs(MatchingRequestStatus.FAILED);
        assertThat(matchingRequest.getStatusReason()).isSameAs(MatchingRequestStatusReason.NO_AVAILABLE_INSTRUCTOR);
        assertThat(result.matchingStatus()).isSameAs(MatchingStatus.NO_AVAILABLE_INSTRUCTOR);
        assertThat(result.requestStatus()).isSameAs(MatchingRequestStatus.FAILED);
        assertThat(result.requestStatusReason()).isSameAs(MatchingRequestStatusReason.NO_AVAILABLE_INSTRUCTOR);
        verifyNoInteractions(instructorMatchingSettingRepository);
        verifyNoInteractions(matchingRequestGroupRepository);
        verifyNoInteractions(matchingOfferRepository);
        ArgumentCaptor<MatchingRequestStatusChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(MatchingRequestStatusChangedEvent.class);
        verify(matchingEventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventId()).isNotNull();
        assertThat(eventCaptor.getValue().occurredAt()).isEqualTo(FIXED_CLOCK.instant());
        assertThat(eventCaptor.getValue().matchingRequestId()).isEqualTo(1L);
    }

    @Test
    void search는_만료_실패_이벤트도_트랜잭션_커밋후에_발행한다() {
        MatchingSearchService service = createService();
        MatchingRequest matchingRequest = matchingRequest(1L, 2, List.of(120, 180), Instant.parse("2026-07-06T23:59:59Z"));
        when(matchingRequestRepository.findByIdAndStatusForUpdate(1L, MatchingRequestStatus.REQUESTED))
                .thenReturn(Optional.of(matchingRequest));
        TransactionSynchronizationManager.initSynchronization();

        try {
            MatchingSearchResult result = service.search(1L);

            assertThat(result.matchingStatus()).isSameAs(MatchingStatus.NO_AVAILABLE_INSTRUCTOR);
            verify(matchingEventPublisher, never()).publish(any());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verify(matchingEventPublisher).publish(isA(MatchingRequestStatusChangedEvent.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void search는_REQUESTED_상태로_잠금조회되지_않으면_새_작업을_하지_않는다() {
        MatchingSearchService service = createService();
        when(matchingRequestRepository.findByIdAndStatusForUpdate(1L, MatchingRequestStatus.REQUESTED))
                .thenReturn(Optional.empty());

        MatchingSearchResult result = service.search(1L);

        assertThat(result.matchingRequestId()).isEqualTo(1L);
        assertThat(result.matchingStatus()).isNull();
        assertThat(result.requestStatus()).isNull();
        assertThat(result.requestStatusReason()).isNull();
        assertThat(result.groupId()).isNull();
        assertThat(result.groupStatus()).isNull();
        verifyNoInteractions(instructorMatchingSettingRepository);
        verifyNoInteractions(matchingRequestGroupRepository);
        verifyNoInteractions(matchingRequestGroupItemRepository);
        verifyNoInteractions(matchingOfferRepository);
        verify(matchingEventPublisher, never()).publish(any());
    }

    @Test
    void search는_요청인원이_강사최대인원보다_적어도_수용가능하면_강사제안을_생성한다() {
        MatchingSearchService service = createService();
        MatchingRequest matchingRequest = matchingRequest(1L, 2, List.of(240, 120, 180), Instant.parse("2026-07-07T00:05:00Z"));
        InstructorMatchingSetting setting = instructorMatchingSetting(11L, 101L, 3, List.of(180, 240));
        when(matchingRequestRepository.findByIdAndStatusForUpdate(1L, MatchingRequestStatus.REQUESTED))
                .thenReturn(Optional.of(matchingRequest));
        when(instructorMatchingSettingRepository.findExposedCandidates(
                matchingRequest.getResort(),
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                2,
                matchingRequest.getRequestedDurationMinutes(),
                true
        )).thenReturn(List.of(setting));
        givenLockedAvailableCandidate(matchingRequest, setting);
        when(matchingRequestGroupRepository.save(any(MatchingRequestGroup.class))).thenAnswer(invocation -> {
            MatchingRequestGroup group = invocation.getArgument(0);
            ReflectionTestUtils.setField(group, "id", 20L);
            return group;
        });
        when(matchingOfferRepository.save(any(MatchingOffer.class))).thenAnswer(invocation -> {
            MatchingOffer offer = invocation.getArgument(0);
            ReflectionTestUtils.setField(offer, "id", 30L);
            return offer;
        });

        MatchingSearchResult result = service.search(1L);

        assertThat(matchingRequest.getStatus()).isSameAs(MatchingRequestStatus.GROUPED);
        assertThat(result.matchingStatus()).isSameAs(MatchingStatus.WAITING_FOR_INSTRUCTOR);
        assertThat(result.groupId()).isEqualTo(20L);
        assertThat(result.groupStatus()).isSameAs(MatchingRequestGroupStatus.EXPOSED);
        ArgumentCaptor<MatchingRequestGroup> groupCaptor = ArgumentCaptor.forClass(MatchingRequestGroup.class);
        verify(matchingRequestGroupRepository).save(groupCaptor.capture());
        assertThat(groupCaptor.getValue().getDurationMinutes()).isEqualTo(180);
        verify(matchingRequestGroupItemRepository).save(any(MatchingRequestGroupItem.class));
        verify(matchingOfferRepository).save(any(MatchingOffer.class));
        ArgumentCaptor<MatchingOfferCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(MatchingOfferCreatedEvent.class);
        verify(matchingEventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventId()).isNotNull();
        assertThat(eventCaptor.getValue().occurredAt()).isEqualTo(FIXED_CLOCK.instant());
        assertThat(eventCaptor.getValue().matchingRequestId()).isEqualTo(1L);
        assertThat(eventCaptor.getValue().durationMinutes()).isEqualTo(180);
    }

    @Test
    void search는_팀정원이_맞으면_그룹을_노출하고_강사제안을_생성한다() {
        MatchingSearchService service = createService();
        MatchingRequest matchingRequest = matchingRequest(1L, 2, List.of(120, 180), Instant.parse("2026-07-07T00:05:00Z"));
        InstructorMatchingSetting setting = instructorMatchingSetting(11L, 101L, 2, List.of(60, 120));
        when(matchingRequestRepository.findByIdAndStatusForUpdate(1L, MatchingRequestStatus.REQUESTED))
                .thenReturn(Optional.of(matchingRequest));
        when(instructorMatchingSettingRepository.findExposedCandidates(
                matchingRequest.getResort(),
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                2,
                matchingRequest.getRequestedDurationMinutes(),
                true
        )).thenReturn(List.of(setting));
        givenLockedAvailableCandidate(matchingRequest, setting);
        when(matchingRequestGroupRepository.save(any(MatchingRequestGroup.class))).thenAnswer(invocation -> {
            MatchingRequestGroup group = invocation.getArgument(0);
            ReflectionTestUtils.setField(group, "id", 20L);
            return group;
        });
        when(matchingOfferRepository.save(any(MatchingOffer.class))).thenAnswer(invocation -> {
            MatchingOffer offer = invocation.getArgument(0);
            ReflectionTestUtils.setField(offer, "id", 30L);
            return offer;
        });

        MatchingSearchResult result = service.search(1L);

        assertThat(matchingRequest.getStatus()).isSameAs(MatchingRequestStatus.GROUPED);
        assertThat(result.matchingStatus()).isSameAs(MatchingStatus.WAITING_FOR_INSTRUCTOR);
        assertThat(result.groupId()).isEqualTo(20L);
        assertThat(result.groupStatus()).isSameAs(MatchingRequestGroupStatus.EXPOSED);
        verify(matchingRequestGroupItemRepository).save(any(MatchingRequestGroupItem.class));
        verify(matchingOfferRepository).save(any(MatchingOffer.class));
        verify(matchingEventPublisher).publish(isA(MatchingOfferCreatedEvent.class));
    }

    @Test
    void search는_강사에게_활성_OFFERED_제안이_있으면_중복제안을_만들지_않고_SEARCHING을_유지한다() {
        MatchingSearchService service = createService();
        MatchingRequest matchingRequest = matchingRequest(1L, 2, List.of(120, 180), Instant.parse("2026-07-07T00:05:00Z"));
        InstructorMatchingSetting setting = instructorMatchingSetting(11L, 101L, 2, List.of(60, 120));
        when(matchingRequestRepository.findByIdAndStatusForUpdate(1L, MatchingRequestStatus.REQUESTED))
                .thenReturn(Optional.of(matchingRequest));
        when(instructorMatchingSettingRepository.findExposedCandidates(
                matchingRequest.getResort(),
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                2,
                matchingRequest.getRequestedDurationMinutes(),
                true
        )).thenReturn(List.of(setting));
        when(instructorMatchingSettingRepository.findExposedCandidateByIdForUpdate(
                11L,
                matchingRequest.getResort(),
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                2,
                matchingRequest.getRequestedDurationMinutes(),
                true
        )).thenReturn(Optional.of(setting));
        when(matchingOfferRepository.findByInstructorProfileIdAndStatusForUpdate(101L, MatchingOfferStatus.OFFERED))
                .thenReturn(List.of(MatchingOffer.create(
                        setting.getInstructorProfile(),
                        MatchingRequestGroup.createCandidate(120),
                        FIXED_CLOCK.instant()
                )));

        MatchingSearchResult result = service.search(1L);

        assertThat(result.matchingStatus()).isSameAs(MatchingStatus.SEARCHING);
        assertThat(result.requestStatus()).isSameAs(MatchingRequestStatus.REQUESTED);
        verify(matchingRequestGroupRepository, never()).save(any());
        verify(matchingRequestGroupItemRepository, never()).save(any());
        verify(matchingOfferRepository, never()).save(any());
        verify(matchingEventPublisher, never()).publish(any());
    }

    @Test
    void search는_앞선_후보가_잠금재조회에서_유효하지_않으면_다음_가능후보로_제안한다() {
        MatchingSearchService service = createService();
        MatchingRequest matchingRequest = matchingRequest(1L, 2, List.of(120, 180), Instant.parse("2026-07-07T00:05:00Z"));
        InstructorMatchingSetting staleSetting = instructorMatchingSetting(11L, 101L, 2, List.of(120, 180));
        InstructorMatchingSetting availableSetting = instructorMatchingSetting(12L, 102L, 2, List.of(180, 240));
        when(matchingRequestRepository.findByIdAndStatusForUpdate(1L, MatchingRequestStatus.REQUESTED))
                .thenReturn(Optional.of(matchingRequest));
        when(instructorMatchingSettingRepository.findExposedCandidates(
                matchingRequest.getResort(),
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                2,
                matchingRequest.getRequestedDurationMinutes(),
                true
        )).thenReturn(List.of(staleSetting, availableSetting));
        when(instructorMatchingSettingRepository.findExposedCandidateByIdForUpdate(
                11L,
                matchingRequest.getResort(),
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                2,
                matchingRequest.getRequestedDurationMinutes(),
                true
        )).thenReturn(Optional.empty());
        givenLockedAvailableCandidate(matchingRequest, availableSetting);
        when(matchingRequestGroupRepository.save(any(MatchingRequestGroup.class))).thenAnswer(invocation -> {
            MatchingRequestGroup group = invocation.getArgument(0);
            ReflectionTestUtils.setField(group, "id", 20L);
            return group;
        });
        when(matchingOfferRepository.save(any(MatchingOffer.class))).thenAnswer(invocation -> {
            MatchingOffer offer = invocation.getArgument(0);
            ReflectionTestUtils.setField(offer, "id", 30L);
            return offer;
        });

        MatchingSearchResult result = service.search(1L);

        assertThat(result.matchingStatus()).isSameAs(MatchingStatus.WAITING_FOR_INSTRUCTOR);
        verify(matchingOfferRepository, never())
                .findByInstructorProfileIdAndStatusForUpdate(101L, MatchingOfferStatus.OFFERED);
        ArgumentCaptor<MatchingOffer> offerCaptor = ArgumentCaptor.forClass(MatchingOffer.class);
        verify(matchingOfferRepository).save(offerCaptor.capture());
        assertThat(offerCaptor.getValue().getInstructorProfile()).isSameAs(availableSetting.getInstructorProfile());
    }

    @Test
    void search는_앞선_후보가_활성제안으로_점유되어도_다음_가능후보로_제안한다() {
        MatchingSearchService service = createService();
        MatchingRequest matchingRequest = matchingRequest(1L, 2, List.of(120, 180), Instant.parse("2026-07-07T00:05:00Z"));
        InstructorMatchingSetting busySetting = instructorMatchingSetting(11L, 101L, 2, List.of(60, 120));
        InstructorMatchingSetting availableSetting = instructorMatchingSetting(12L, 102L, 2, List.of(180, 240));
        when(matchingRequestRepository.findByIdAndStatusForUpdate(1L, MatchingRequestStatus.REQUESTED))
                .thenReturn(Optional.of(matchingRequest));
        when(instructorMatchingSettingRepository.findExposedCandidates(
                matchingRequest.getResort(),
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                2,
                matchingRequest.getRequestedDurationMinutes(),
                true
        )).thenReturn(List.of(busySetting, availableSetting));
        when(instructorMatchingSettingRepository.findExposedCandidateByIdForUpdate(
                11L,
                matchingRequest.getResort(),
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                2,
                matchingRequest.getRequestedDurationMinutes(),
                true
        )).thenReturn(Optional.of(busySetting));
        when(matchingOfferRepository.findByInstructorProfileIdAndStatusForUpdate(101L, MatchingOfferStatus.OFFERED))
                .thenReturn(List.of(MatchingOffer.create(
                        busySetting.getInstructorProfile(),
                        MatchingRequestGroup.createCandidate(120),
                        FIXED_CLOCK.instant()
                )));
        givenLockedAvailableCandidate(matchingRequest, availableSetting);
        when(matchingRequestGroupRepository.save(any(MatchingRequestGroup.class))).thenAnswer(invocation -> {
            MatchingRequestGroup group = invocation.getArgument(0);
            ReflectionTestUtils.setField(group, "id", 20L);
            return group;
        });
        when(matchingOfferRepository.save(any(MatchingOffer.class))).thenAnswer(invocation -> {
            MatchingOffer offer = invocation.getArgument(0);
            ReflectionTestUtils.setField(offer, "id", 30L);
            return offer;
        });

        MatchingSearchResult result = service.search(1L);

        assertThat(result.matchingStatus()).isSameAs(MatchingStatus.WAITING_FOR_INSTRUCTOR);
        ArgumentCaptor<MatchingOffer> offerCaptor = ArgumentCaptor.forClass(MatchingOffer.class);
        verify(matchingOfferRepository).save(offerCaptor.capture());
        assertThat(offerCaptor.getValue().getInstructorProfile()).isSameAs(availableSetting.getInstructorProfile());
        ArgumentCaptor<MatchingRequestGroup> groupCaptor = ArgumentCaptor.forClass(MatchingRequestGroup.class);
        verify(matchingRequestGroupRepository).save(groupCaptor.capture());
        assertThat(groupCaptor.getValue().getDurationMinutes()).isEqualTo(180);
    }

    @Test
    void search는_같은_매칭요청에서_이미_제안받은_강사를_건너뛰고_다음_후보로_제안한다() {
        MatchingSearchService service = createService();
        MatchingRequest matchingRequest = matchingRequest(1L, 2, List.of(120, 180), Instant.parse("2026-07-07T00:05:00Z"));
        InstructorMatchingSetting previouslyOfferedSetting = instructorMatchingSetting(11L, 101L, 2, List.of(60, 120));
        InstructorMatchingSetting availableSetting = instructorMatchingSetting(12L, 102L, 2, List.of(180, 240));
        when(matchingRequestRepository.findByIdAndStatusForUpdate(1L, MatchingRequestStatus.REQUESTED))
                .thenReturn(Optional.of(matchingRequest));
        when(instructorMatchingSettingRepository.findExposedCandidates(
                matchingRequest.getResort(),
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                2,
                matchingRequest.getRequestedDurationMinutes(),
                true
        )).thenReturn(List.of(previouslyOfferedSetting, availableSetting));
        when(instructorMatchingSettingRepository.findExposedCandidateByIdForUpdate(
                11L,
                matchingRequest.getResort(),
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                2,
                matchingRequest.getRequestedDurationMinutes(),
                true
        )).thenReturn(Optional.of(previouslyOfferedSetting));
        when(matchingOfferRepository.findByInstructorProfileIdAndStatusForUpdate(101L, MatchingOfferStatus.OFFERED))
                .thenReturn(List.of());
        when(matchingOfferRepository.existsByMatchingRequestIdAndInstructorProfileId(1L, 101L))
                .thenReturn(true);
        givenLockedAvailableCandidate(matchingRequest, availableSetting);
        when(matchingRequestGroupRepository.save(any(MatchingRequestGroup.class))).thenAnswer(invocation -> {
            MatchingRequestGroup group = invocation.getArgument(0);
            ReflectionTestUtils.setField(group, "id", 20L);
            return group;
        });
        when(matchingOfferRepository.save(any(MatchingOffer.class))).thenAnswer(invocation -> {
            MatchingOffer offer = invocation.getArgument(0);
            ReflectionTestUtils.setField(offer, "id", 30L);
            return offer;
        });

        MatchingSearchResult result = service.search(1L);

        assertThat(result.matchingStatus()).isSameAs(MatchingStatus.WAITING_FOR_INSTRUCTOR);
        ArgumentCaptor<MatchingOffer> offerCaptor = ArgumentCaptor.forClass(MatchingOffer.class);
        verify(matchingOfferRepository).save(offerCaptor.capture());
        assertThat(offerCaptor.getValue().getInstructorProfile()).isSameAs(availableSetting.getInstructorProfile());
    }

    @Test
    void search는_트랜잭션_동기화가_있으면_제안생성_이벤트를_커밋후에_발행한다() {
        MatchingSearchService service = createService();
        MatchingRequest matchingRequest = matchingRequest(1L, 2, List.of(120, 180), Instant.parse("2026-07-07T00:05:00Z"));
        InstructorMatchingSetting setting = instructorMatchingSetting(11L, 101L, 2, List.of(60, 120));
        when(matchingRequestRepository.findByIdAndStatusForUpdate(1L, MatchingRequestStatus.REQUESTED))
                .thenReturn(Optional.of(matchingRequest));
        when(instructorMatchingSettingRepository.findExposedCandidates(
                matchingRequest.getResort(),
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                2,
                matchingRequest.getRequestedDurationMinutes(),
                true
        )).thenReturn(List.of(setting));
        givenLockedAvailableCandidate(matchingRequest, setting);
        when(matchingRequestGroupRepository.save(any(MatchingRequestGroup.class))).thenAnswer(invocation -> {
            MatchingRequestGroup group = invocation.getArgument(0);
            ReflectionTestUtils.setField(group, "id", 20L);
            return group;
        });
        when(matchingOfferRepository.save(any(MatchingOffer.class))).thenAnswer(invocation -> {
            MatchingOffer offer = invocation.getArgument(0);
            ReflectionTestUtils.setField(offer, "id", 30L);
            return offer;
        });
        TransactionSynchronizationManager.initSynchronization();

        try {
            service.search(1L);

            verify(matchingEventPublisher, never()).publish(any());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verify(matchingEventPublisher).publish(isA(MatchingOfferCreatedEvent.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void ensureNextOfferForGroup은_그룹에_이미_OFFERED_제안이_있으면_새_제안을_만들지_않는다() {
        MatchingSearchService service = createService();
        MatchingRequest matchingRequest = matchingRequest(1L, 2, List.of(120, 180), Instant.parse("2026-07-07T00:05:00Z"));
        MatchingRequestGroup group = MatchingRequestGroup.createCandidate(120);
        group.expose();
        ReflectionTestUtils.setField(group, "id", 20L);
        MatchingOffer activeOffer = MatchingOffer.create(
                instructorProfile(101L),
                group,
                FIXED_CLOCK.instant(),
                FIXED_CLOCK.instant().plusSeconds(60)
        );
        ReflectionTestUtils.setField(activeOffer, "id", 30L);
        when(matchingRequestGroupRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(group));
        when(matchingOfferRepository.findByMatchingRequestGroupIdAndStatusForUpdate(20L, MatchingOfferStatus.OFFERED))
                .thenReturn(List.of(activeOffer));

        NextMatchingOfferResult result = service.ensureNextOfferForGroup(
                matchingRequest,
                group,
                FIXED_CLOCK.instant()
        );

        assertThat(result.status()).isSameAs(NextMatchingOfferResult.Status.ALREADY_ACTIVE);
        assertThat(result.matchingOffer()).isSameAs(activeOffer);
        verifyNoInteractions(instructorMatchingSettingRepository);
        verify(matchingOfferRepository, never()).save(any());
        verify(matchingEventPublisher, never()).publish(any());
    }

    @Test
    void ensureNextOfferForGroup은_활성_제안이_없으면_현재_후보를_다시_조회해_새_제안을_만든다() {
        MatchingSearchService service = createService();
        MatchingRequest matchingRequest = matchingRequest(1L, 2, List.of(120, 180), Instant.parse("2026-07-07T00:05:00Z"));
        MatchingRequestGroup group = MatchingRequestGroup.createCandidate(120);
        group.expose();
        ReflectionTestUtils.setField(group, "id", 20L);
        InstructorMatchingSetting currentPrioritySetting = instructorMatchingSetting(12L, 102L, 2, List.of(120, 180));
        when(matchingRequestGroupRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(group));
        when(matchingOfferRepository.findByMatchingRequestGroupIdAndStatusForUpdate(20L, MatchingOfferStatus.OFFERED))
                .thenReturn(List.of());
        when(instructorMatchingSettingRepository.findExposedCandidates(
                matchingRequest.getResort(),
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                2,
                matchingRequest.getRequestedDurationMinutes(),
                true
        )).thenReturn(List.of(currentPrioritySetting));
        givenLockedAvailableCandidate(matchingRequest, currentPrioritySetting);
        when(matchingOfferRepository.save(any(MatchingOffer.class))).thenAnswer(invocation -> {
            MatchingOffer offer = invocation.getArgument(0);
            ReflectionTestUtils.setField(offer, "id", 30L);
            return offer;
        });

        NextMatchingOfferResult result = service.ensureNextOfferForGroup(
                matchingRequest,
                group,
                FIXED_CLOCK.instant()
        );

        assertThat(result.status()).isSameAs(NextMatchingOfferResult.Status.CREATED);
        assertThat(result.matchingOffer().getInstructorProfile()).isSameAs(currentPrioritySetting.getInstructorProfile());
        assertThat(result.matchingOffer().getMatchingRequestGroup()).isSameAs(group);
        verify(matchingOfferRepository).save(any(MatchingOffer.class));
        verify(matchingEventPublisher).publish(isA(MatchingOfferCreatedEvent.class));
    }

    private MatchingSearchService createService() {
        return new MatchingSearchService(
                matchingRequestRepository,
                matchingRequestGroupRepository,
                matchingRequestGroupItemRepository,
                matchingOfferRepository,
                instructorMatchingSettingRepository,
                new MatchingStatusResolver(),
                matchingEventPublisher,
                new MatchingAfterCommitExecutor(),
                FIXED_CLOCK
        );
    }

    private MatchingRequest matchingRequest(
            Long id,
            int headcount,
            List<Integer> requestedDurationMinutes,
            Instant expiresAt
    ) {
        MatchingRequest matchingRequest = MatchingRequest.create(
                Member.create("소비자", null, MemberRole.CONSUMER, MemberStatus.ACTIVE),
                resort(),
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                headcount,
                requestedDurationMinutes,
                true,
                expiresAt
        );
        ReflectionTestUtils.setField(matchingRequest, "id", id);
        return matchingRequest;
    }

    private void givenLockedAvailableCandidate(
            MatchingRequest matchingRequest,
            InstructorMatchingSetting setting
    ) {
        when(instructorMatchingSettingRepository.findExposedCandidateByIdForUpdate(
                setting.getId(),
                matchingRequest.getResort(),
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                matchingRequest.getHeadcount(),
                matchingRequest.getRequestedDurationMinutes(),
                true
        )).thenReturn(Optional.of(setting));
        when(matchingOfferRepository.findByInstructorProfileIdAndStatusForUpdate(
                setting.getInstructorProfile().getId(),
                MatchingOfferStatus.OFFERED
        )).thenReturn(List.of());
    }

    private InstructorMatchingSetting instructorMatchingSetting(
            Long id,
            Long instructorProfileId,
            int maxHeadcount,
            List<Integer> availableDurationMinutes
    ) {
        InstructorProfile instructorProfile = instructorProfile(instructorProfileId);
        InstructorMatchingSetting setting = InstructorMatchingSetting.create(
                instructorProfile,
                Sport.SNOWBOARD,
                List.of(LessonLevel.FIRST_TIME, LessonLevel.BEGINNER),
                availableDurationMinutes,
                maxHeadcount,
                true
        );
        ReflectionTestUtils.setField(setting, "id", id);
        ReflectionTestUtils.setField(setting, "instructorProfile", instructorProfile);
        return setting;
    }

    private InstructorProfile instructorProfile(Long id) {
        Member member = Member.create("강사", null, MemberRole.INSTRUCTOR, MemberStatus.ACTIVE);
        InstructorProfile instructorProfile = InstructorProfile.create(
                member,
                "강사",
                "010-0000-0000",
                Gender.MALE,
                LocalDate.of(2000, 1, 1),
                "테스트 강사 프로필",
                LocalDate.of(2020, 1, 1),
                InstructorApprovalStatus.APPROVED,
                FIXED_CLOCK.instant()
        );
        ReflectionTestUtils.setField(instructorProfile, "id", id);
        return instructorProfile;
    }

    private Resort resort() {
        try {
            Constructor<Resort> constructor = Resort.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Resort resort = constructor.newInstance();
            ReflectionTestUtils.setField(resort, "code", "HIGH1");
            ReflectionTestUtils.setField(resort, "name", "하이원리조트");
            ReflectionTestUtils.setField(resort, "displayName", "하이원");
            return resort;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
