package org.sopt.ssingserver.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.matching.dto.command.MatchingCreationCommand;
import org.sopt.ssingserver.domain.matching.dto.command.MatchingParticipantCommand;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingCreationResult;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.error.MatchingErrorCode;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestParticipantRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestRepository;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.Gender;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.member.repository.MemberRepository;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.sopt.ssingserver.domain.resort.repository.ResortRepository;
import org.sopt.ssingserver.global.error.BusinessException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class MatchingOrchestrationServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-07T00:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ResortRepository resortRepository;

    @Mock
    private MatchingRequestRepository matchingRequestRepository;

    @Mock
    private MatchingRequestParticipantRepository matchingRequestParticipantRepository;

    @Mock
    private MatchingSearchTriggerService matchingSearchTriggerService;

    @Test
    void createImmediateMatchingRequest는_요청을_REQUESTED로_저장하고_SEARCHING_결과를_반환한다() {
        MatchingOrchestrationService service = createService();
        Member member = member();
        Resort resort = resort();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(resortRepository.findById(2L)).thenReturn(Optional.of(resort));
        when(matchingRequestRepository.save(any(MatchingRequest.class))).thenAnswer(invocation -> {
            MatchingRequest matchingRequest = invocation.getArgument(0);
            ReflectionTestUtils.setField(matchingRequest, "id", 10L);
            return matchingRequest;
        });

        MatchingCreationResult result = service.createImmediateMatchingRequest(command(2));

        assertThat(result.matchingRequestId()).isEqualTo(10L);
        assertThat(result.matchingStatus()).isSameAs(MatchingStatus.SEARCHING);
        assertThat(result.requestStatus()).isSameAs(MatchingRequestStatus.REQUESTED);
        assertThat(result.requestStatusReason()).isNull();
        assertThat(result.groupId()).isNull();
        assertThat(result.groupStatus()).isNull();
        assertThat(result.expiresAt()).isEqualTo(Instant.parse("2026-07-07T00:05:00Z"));

        ArgumentCaptor<MatchingRequest> matchingRequestCaptor = ArgumentCaptor.forClass(MatchingRequest.class);
        verify(matchingRequestRepository).save(matchingRequestCaptor.capture());
        assertThat(matchingRequestCaptor.getValue().getMember()).isSameAs(member);
        assertThat(matchingRequestCaptor.getValue().getResort()).isSameAs(resort);
        assertThat(matchingRequestCaptor.getValue().getRequestedDurationMinutes()).isEqualTo(120);
        assertThat(matchingRequestCaptor.getValue().getStatus()).isSameAs(MatchingRequestStatus.REQUESTED);
        verify(matchingRequestParticipantRepository).saveAll(any());
        verify(matchingSearchTriggerService).triggerSearch(10L);
    }

    @Test
    void createImmediateMatchingRequest는_트랜잭션_동기화가_있으면_커밋후_즉시탐색을_트리거한다() {
        MatchingOrchestrationService service = createService();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member()));
        when(resortRepository.findById(2L)).thenReturn(Optional.of(resort()));
        when(matchingRequestRepository.save(any(MatchingRequest.class))).thenAnswer(invocation -> {
            MatchingRequest matchingRequest = invocation.getArgument(0);
            ReflectionTestUtils.setField(matchingRequest, "id", 10L);
            return matchingRequest;
        });
        TransactionSynchronizationManager.initSynchronization();

        try {
            service.createImmediateMatchingRequest(command(2));

            verifyNoInteractions(matchingSearchTriggerService);

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verify(matchingSearchTriggerService).triggerSearch(10L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void createImmediateMatchingRequest는_headcount와_참여자수가_다르면_저장하지_않는다() {
        MatchingOrchestrationService service = createService();
        MatchingCreationCommand command = command(2, List.of(
                MatchingParticipantCommand.of(24, Gender.FEMALE)
        ));

        assertThatThrownBy(() -> service.createImmediateMatchingRequest(command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isSameAs(MatchingErrorCode.MATCHING_PARTICIPANT_COUNT_MISMATCH);

        verifyNoInteractions(memberRepository);
        verifyNoInteractions(resortRepository);
        verifyNoInteractions(matchingRequestRepository);
        verifyNoInteractions(matchingRequestParticipantRepository);
        verifyNoInteractions(matchingSearchTriggerService);
    }

    @Test
    void createImmediateMatchingRequest는_회원을_찾지_못하면_요청을_저장하지_않는다() {
        MatchingOrchestrationService service = createService();
        when(memberRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createImmediateMatchingRequest(command(2)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isSameAs(MatchingErrorCode.MATCHING_MEMBER_NOT_FOUND);

        verifyNoInteractions(resortRepository);
        verifyNoInteractions(matchingRequestRepository);
        verifyNoInteractions(matchingRequestParticipantRepository);
        verifyNoInteractions(matchingSearchTriggerService);
    }

    @Test
    void createImmediateMatchingRequest는_리조트를_찾지_못하면_요청을_저장하지_않는다() {
        MatchingOrchestrationService service = createService();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member()));
        when(resortRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createImmediateMatchingRequest(command(2)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isSameAs(MatchingErrorCode.MATCHING_RESORT_NOT_FOUND);

        verifyNoInteractions(matchingRequestRepository);
        verifyNoInteractions(matchingRequestParticipantRepository);
        verifyNoInteractions(matchingSearchTriggerService);
    }

    private MatchingOrchestrationService createService() {
        return new MatchingOrchestrationService(
                memberRepository,
                resortRepository,
                matchingRequestRepository,
                matchingRequestParticipantRepository,
                matchingSearchTriggerService,
                new MatchingAfterCommitExecutor(),
                FIXED_CLOCK
        );
    }

    private MatchingCreationCommand command(int headcount) {
        return command(headcount, List.of(
                MatchingParticipantCommand.of(24, Gender.FEMALE),
                MatchingParticipantCommand.of(25, Gender.MALE)
        ));
    }

    private MatchingCreationCommand command(
            int headcount,
            List<MatchingParticipantCommand> participants
    ) {
        return MatchingCreationCommand.of(
                1L,
                2L,
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                headcount,
                120,
                true,
                participants
        );
    }

    private Member member() {
        return Member.create("소비자", null, MemberRole.CONSUMER, MemberStatus.ACTIVE);
    }

    private Resort resort() {
        try {
            Constructor<Resort> constructor = Resort.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Resort resort = constructor.newInstance();
            ReflectionTestUtils.setField(resort, "code", "HIGH1");
            ReflectionTestUtils.setField(resort, "name", "하이원");
            return resort;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
