package org.sopt.ssingserver.domain.matching.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.matching.dto.command.MatchingCreationCommand;
import org.sopt.ssingserver.domain.matching.dto.command.MatchingParticipantCommand;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingCreationResult;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestParticipant;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.error.MatchingErrorCode;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestParticipantRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestRepository;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.repository.MemberRepository;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.sopt.ssingserver.domain.resort.repository.ResortRepository;
import org.sopt.ssingserver.global.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 소비자 매칭 요청 생성 시작점, 요청 저장과 생성 직후 SEARCHING 응답 구성
@Service
@RequiredArgsConstructor
public class MatchingOrchestrationService {

    private final MemberRepository memberRepository;
    private final ResortRepository resortRepository;
    private final MatchingRequestRepository matchingRequestRepository;
    private final MatchingRequestParticipantRepository matchingRequestParticipantRepository;
    private final MatchingSearchTriggerService matchingSearchTriggerService;
    private final MatchingAfterCommitExecutor matchingAfterCommitExecutor;

    // 요청 생성 API 호출 지점, DB REQUESTED 저장과 API SEARCHING 응답 고정
    @Transactional
    public MatchingCreationResult createImmediateMatchingRequest(MatchingCreationCommand command) {
        // 활성 요청이 0건인 순간의 동시 POST도 같은 회원 row에서 직렬화한다.
        Member member = getMember(command.memberId());
        validateNoActiveMatchingRequest(command.memberId());

        // Controller의 DB 조회 책임 방지를 위한 Service 내부 리조트 존재 검증
        Resort resort = getResort(command.resortCode());

        // 기본 무제한 탐색 정책 적용, DB SEARCHING 미저장과 REQUESTED 상태만 저장
        MatchingRequest matchingRequest = matchingRequestRepository.save(MatchingRequest.createUnlimitedSearch(
                member,
                resort,
                command.sport(),
                command.lessonLevel(),
                command.headcount(),
                command.requestedDurationMinutes(),
                command.isEquipmentReady()
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

    // 공통 afterCommit 실행기 위임을 통한 생성 커밋 이후 재탐색 시작
    private void triggerSearchAfterCommit(Long matchingRequestId) {
        matchingAfterCommitExecutor.execute(
                "matching-request-created-search",
                () -> matchingSearchTriggerService.triggerSearch(matchingRequestId)
        );
    }

    // 매칭 요청 소유 회원 조회와 요청 저장 전 명시적 실패 처리
    private Member getMember(Long memberId) {
        return memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.MATCHING_MEMBER_NOT_FOUND));
    }

    private void validateNoActiveMatchingRequest(Long memberId) {
        boolean hasActiveMatchingRequest = matchingRequestRepository.existsByMemberIdAndStatusIn(
                memberId,
                MatchingRequestStatus.activeNegotiationStatuses()
        );
        if (hasActiveMatchingRequest) {
            throw new BusinessException(MatchingErrorCode.MATCHING_REQUEST_ALREADY_EXISTS);
        }
    }

    // 매칭 요청 대상 리조트 code 조회와 요청 저장 전 명시적 실패 처리
    private Resort getResort(String resortCode) {
        return resortRepository.findByCode(resortCode)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.MATCHING_RESORT_NOT_FOUND));
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
                participant.name(),
                participant.age(),
                participant.gender()
        );
    }
}
