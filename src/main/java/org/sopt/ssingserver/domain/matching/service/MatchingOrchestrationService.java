package org.sopt.ssingserver.domain.matching.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.matching.dto.command.MatchingCreationCommand;
import org.sopt.ssingserver.domain.matching.dto.command.MatchingParticipantCommand;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingCreationResult;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestParticipant;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestParticipantRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// 소비자 매칭 요청 생성 시작점, 요청 저장과 생성 직후 SEARCHING 응답 구성
@Service
@RequiredArgsConstructor
public class MatchingOrchestrationService {

    private static final Duration SEARCH_TIMEOUT = Duration.ofMinutes(5);

    private final MatchingRequestRepository matchingRequestRepository;
    private final MatchingRequestParticipantRepository matchingRequestParticipantRepository;
    private final MatchingSearchTriggerService matchingSearchTriggerService;
    private final Clock clock;

    // 요청 생성 API 호출 지점, DB REQUESTED 저장과 API SEARCHING 응답 고정
    @Transactional
    public MatchingCreationResult createImmediateMatchingRequest(MatchingCreationCommand command) {
        // 소비자 입력 총 인원과 참여자 상세 목록 불일치에 따른 팀 구성 기준 오염 방지
        validateParticipantCount(command);

        // 요청 생성 시각 기준 SEARCHING 재탐색 최대 시간 5분 계산
        Instant expiresAt = clock.instant().plus(SEARCH_TIMEOUT);

        // DB SEARCHING 미저장, 실제 요청 상태 REQUESTED와 탐색 만료 시각 저장
        MatchingRequest matchingRequest = matchingRequestRepository.save(MatchingRequest.create(
                command.member(),
                command.resort(),
                command.sport(),
                command.lessonLevel(),
                command.headcount(),
                command.requestedDurationMinutes(),
                command.isEquipmentReady(),
                expiresAt
        ));

        // 요청 row 생성 이후 참여자별 나이/성별 정보와 같은 요청 연결 저장
        matchingRequestParticipantRepository.saveAll(createParticipants(command, matchingRequest));

        // 생성 직후 REST 응답의 최초 탐색 결과와 무관한 SEARCHING 계약 적용
        MatchingCreationResult result = MatchingCreationResult.searching(matchingRequest);

        // 요청 저장 트랜잭션 커밋 이후 즉시 탐색 시작
        // 아직 저장되지 않은 요청 기준 그룹/제안/알림 생성 방지
        triggerSearchAfterCommit(matchingRequest.getId());
        return result;
    }

    // Spring 트랜잭션 내부 afterCommit 등록, 단위 테스트 등 트랜잭션 부재 시 즉시 실행
    private void triggerSearchAfterCommit(Long matchingRequestId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            matchingSearchTriggerService.triggerSearch(matchingRequestId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                matchingSearchTriggerService.triggerSearch(matchingRequestId);
            }
        });
    }

    // 요청 인원과 참여자 목록 수 일치 검증을 통한 이후 그룹 생성 기준 통일
    private void validateParticipantCount(MatchingCreationCommand command) {
        if (command.headcount() != command.participants().size()) {
            throw new IllegalArgumentException("headcount must match participants size.");
        }
    }

    // command 참여자 값의 DB 저장용 MatchingRequestParticipant 엔티티 목록 변환
    private List<MatchingRequestParticipant> createParticipants(
            MatchingCreationCommand command,
            MatchingRequest matchingRequest
    ) {
        return command.participants().stream()
                .map(participant -> createParticipant(matchingRequest, participant))
                .toList();
    }

    // 참여자 1명의 입력값과 현재 생성된 매칭 요청 소속 참여자 엔티티 연결
    private MatchingRequestParticipant createParticipant(
            MatchingRequest matchingRequest,
            MatchingParticipantCommand participant
    ) {
        return MatchingRequestParticipant.create(
                matchingRequest,
                participant.age(),
                participant.gender()
        );
    }
}
