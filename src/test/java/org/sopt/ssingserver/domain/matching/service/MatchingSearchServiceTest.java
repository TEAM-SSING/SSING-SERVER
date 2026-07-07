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
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroup;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroupItem;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
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
    void search는_후보가_없어도_만료전이면_REQUESTED_SEARCHING을_유지한다() {
        MatchingSearchService service = createService();
        MatchingRequest matchingRequest = matchingRequest(1L, 2, List.of(120, 180), Instant.parse("2026-07-07T00:05:00Z"));
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
        MatchingRequest matchingRequest = matchingRequest(1L, 2, List.of(120, 180), Instant.parse("2026-07-07T00:05:00Z"));
        InstructorMatchingSetting setting = instructorMatchingSetting(3);
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
        ArgumentCaptor<MatchingOfferCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(MatchingOfferCreatedEvent.class);
        verify(matchingEventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventId()).isNotNull();
        assertThat(eventCaptor.getValue().occurredAt()).isEqualTo(FIXED_CLOCK.instant());
        assertThat(eventCaptor.getValue().matchingRequestId()).isEqualTo(1L);
    }

    @Test
    void search는_팀정원이_맞으면_그룹을_노출하고_강사제안을_생성한다() {
        MatchingSearchService service = createService();
        MatchingRequest matchingRequest = matchingRequest(1L, 2, List.of(120, 180), Instant.parse("2026-07-07T00:05:00Z"));
        InstructorMatchingSetting setting = instructorMatchingSetting(2);
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
    void search는_트랜잭션_동기화가_있으면_제안생성_이벤트를_커밋후에_발행한다() {
        MatchingSearchService service = createService();
        MatchingRequest matchingRequest = matchingRequest(1L, 2, List.of(120, 180), Instant.parse("2026-07-07T00:05:00Z"));
        InstructorMatchingSetting setting = instructorMatchingSetting(2);
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

    private InstructorMatchingSetting instructorMatchingSetting(int maxHeadcount) {
        return InstructorMatchingSetting.create(
                instructorProfile(),
                resort(),
                Sport.SNOWBOARD,
                List.of(LessonLevel.FIRST_TIME, LessonLevel.BEGINNER),
                List.of(60, 120),
                maxHeadcount,
                true
        );
    }

    private InstructorProfile instructorProfile() {
        Member member = Member.create("강사", null, MemberRole.INSTRUCTOR, MemberStatus.ACTIVE);
        return InstructorProfile.create(
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
