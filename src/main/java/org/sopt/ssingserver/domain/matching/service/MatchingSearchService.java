package org.sopt.ssingserver.domain.matching.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.instructor.entity.InstructorMatchingSetting;
import org.sopt.ssingserver.domain.instructor.repository.InstructorMatchingSettingRepository;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingSearchResult;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroup;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroupItem;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.matching.event.MatchingDomainEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingEventPublisher;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferCreatedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingRequestStatusChangedEvent;
import org.sopt.ssingserver.domain.matching.repository.MatchingOfferRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestGroupItemRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestGroupRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// SEARCHING 중인 매칭 요청 재탐색과 만료/후보/그룹/제안 생성 결정 서비스
@Service
@RequiredArgsConstructor
public class MatchingSearchService {

    private final MatchingRequestRepository matchingRequestRepository;
    private final MatchingRequestGroupRepository matchingRequestGroupRepository;
    private final MatchingRequestGroupItemRepository matchingRequestGroupItemRepository;
    private final MatchingOfferRepository matchingOfferRepository;
    private final InstructorMatchingSettingRepository instructorMatchingSettingRepository;
    private final MatchingStatusResolver matchingStatusResolver;
    private final MatchingEventPublisher matchingEventPublisher;
    private final Clock clock;

    // 즉시 트리거와 스케줄러의 단건 탐색 입구, REQUESTED 요청 잠금 조회 처리
    @Transactional
    public MatchingSearchResult search(Long matchingRequestId) {
        // 같은 요청 동시 처리와 중복 그룹/제안 생성 방지를 위한 REQUESTED row DB lock
        return matchingRequestRepository.findByIdAndStatusForUpdate(
                        matchingRequestId,
                        MatchingRequestStatus.REQUESTED
                )
                .map(this::searchRequestedRequest)
                // 다른 흐름에서 이미 상태 변경된 요청의 새 작업 없는 멱등 SEARCHING 결과 반환
                .orElseGet(() -> MatchingSearchResult.searching(matchingRequestId));
    }

    // lock 확보 REQUESTED 요청 1건의 만료 여부, 후보 존재 여부, 그룹/제안 생성 판단
    private MatchingSearchResult searchRequestedRequest(MatchingRequest matchingRequest) {
        Instant now = clock.instant();

        // 탐색 가능 시간 종료 요청의 최종 실패 전환과 이후 재탐색 대상 제외
        if (matchingRequest.isSearchExpired(now)) {
            matchingRequest.failNoAvailableInstructor();
            MatchingStatus matchingStatus = matchingStatusResolver.resolve(
                    matchingRequest,
                    false,
                    Optional.empty(),
                    Optional.empty()
            );

            // 생성 직후 SEARCHING 이벤트 미발행, 만료 실패 등 이후 상태 변경의 이벤트 경계 전달
            publishAfterCommit(new MatchingRequestStatusChangedEvent(
                    matchingRequest.getId(),
                    matchingRequest.getStatus(),
                    matchingRequest.getStatusReason(),
                    matchingStatus
            ));
            return MatchingSearchResult.of(matchingRequest, matchingStatus);
        }

        // 소비자 요청 조건과 강사 노출 조건을 모두 만족하는 후보의 Repository 쿼리 필터링
        List<InstructorMatchingSetting> candidates = instructorMatchingSettingRepository.findExposedCandidates(
                matchingRequest.getResort(),
                matchingRequest.getSport(),
                matchingRequest.getLessonLevel(),
                matchingRequest.getHeadcount(),
                matchingRequest.getRequestedDurationMinutes(),
                matchingRequest.isEquipmentReady()
        );

        // 후보 없음 상태의 즉시 실패 방지, REQUESTED 유지와 다음 트리거/스케줄러 재탐색
        if (candidates.isEmpty()) {
            MatchingStatus matchingStatus = matchingStatusResolver.resolve(
                    matchingRequest,
                    false,
                    Optional.empty(),
                    Optional.empty()
            );
            return MatchingSearchResult.of(matchingRequest, matchingStatus);
        }

        // 현재 MVP의 repository id 오름차순 첫 후보 제안
        // TODO: 점수 기반 랭킹 확정 시 selectCandidate의 거리/응답률/평점 합산 후보 선택
        return createGroupAndOffer(matchingRequest, selectCandidate(candidates), now);
    }

    // 후보 존재 요청의 그룹 편입과 강사 제안 생성
    private MatchingSearchResult createGroupAndOffer(
            MatchingRequest matchingRequest,
            InstructorMatchingSetting candidate,
            Instant now
    ) {
        // 현재 요청 포함 후보 그룹 생성과 요청-그룹 연결 row 우선 저장
        MatchingRequestGroup matchingRequestGroup = matchingRequestGroupRepository.save(
                MatchingRequestGroup.createCandidate()
        );
        matchingRequestGroupItemRepository.save(MatchingRequestGroupItem.createNotRequested(
                matchingRequest,
                matchingRequestGroup
        ));

        // 그룹/제안 생성 요청의 GROUPED 전환과 재탐색 대상 제외
        matchingRequest.markGrouped();

        // maxHeadcount의 목표 정원 아닌 수용 가능한 최대 인원 의미
        // 후보 쿼리의 maxHeadcount >= 요청 인원 확인 이후 즉시 제안 생성
        matchingRequestGroup.expose();
        MatchingOffer matchingOffer = matchingOfferRepository.save(MatchingOffer.create(
                candidate.getInstructorProfile(),
                matchingRequestGroup,
                now
        ));

        // 실제 WebSocket/FCM 부재 상태의 제안 생성 이벤트 포트 전달과 후속 구현 연결 지점
        publishAfterCommit(new MatchingOfferCreatedEvent(
                matchingRequest.getId(),
                matchingRequestGroup.getId(),
                matchingOffer.getId(),
                candidate.getInstructorProfile().getId()
        ));

        return MatchingSearchResult.of(
                matchingRequest,
                MatchingStatus.WAITING_FOR_INSTRUCTOR,
                matchingRequestGroup
        );
    }

    // 현재 후보 목록의 repository 정렬 결과 사용, 향후 랭킹 정책 교체 지점
    private InstructorMatchingSetting selectCandidate(List<InstructorMatchingSetting> candidates) {
        return candidates.get(0);
    }

    // DB 변경 커밋 이후 상태 변경 이벤트의 알림 계층 전달
    // 트랜잭션 없는 단위 테스트/직접 호출 환경의 즉시 발행과 같은 코드 경로 검증
    private void publishAfterCommit(MatchingDomainEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            matchingEventPublisher.publish(event);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                matchingEventPublisher.publish(event);
            }
        });
    }

}
