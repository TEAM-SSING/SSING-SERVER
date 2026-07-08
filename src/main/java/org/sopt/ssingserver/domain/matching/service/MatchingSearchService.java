package org.sopt.ssingserver.domain.matching.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.instructor.entity.InstructorMatchingSetting;
import org.sopt.ssingserver.domain.instructor.repository.InstructorMatchingSettingRepository;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingSearchResult;
import org.sopt.ssingserver.domain.matching.dto.result.NextMatchingOfferResult;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroup;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroupItem;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
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
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// SEARCHING 중인 매칭 요청 재탐색과 만료/후보/그룹/제안 생성 결정 서비스
@Service
@RequiredArgsConstructor
public class MatchingSearchService {

    // 매칭 시간 정책의 MVP 기준: 강사 제안 응답 제한 시간 1분
    private static final Duration INSTRUCTOR_RESPONSE_TIMEOUT = Duration.ofMinutes(1);

    private final MatchingRequestRepository matchingRequestRepository;
    private final MatchingRequestGroupRepository matchingRequestGroupRepository;
    private final MatchingRequestGroupItemRepository matchingRequestGroupItemRepository;
    private final MatchingOfferRepository matchingOfferRepository;
    private final InstructorMatchingSettingRepository instructorMatchingSettingRepository;
    private final MatchingStatusResolver matchingStatusResolver;
    private final MatchingEventPublisher matchingEventPublisher;
    private final MatchingAfterCommitExecutor matchingAfterCommitExecutor;
    private final Clock clock;

    // 즉시 트리거와 스케줄러의 단건 탐색 입구, REQUESTED 요청 잠금 조회 처리
    @Transactional
    public MatchingSearchResult search(Long matchingRequestId) {
        // 같은 요청 동시 처리와 중복 그룹/제안 생성 방지를 위한 REQUESTED row DB lock
        // 잠금 조회 실패 시 이미 만료/그룹화/실패 처리된 요청으로 보고 새 작업 생략
        return matchingRequestRepository.findByIdAndStatusForUpdate(
                        matchingRequestId,
                        MatchingRequestStatus.REQUESTED
                )
                .map(this::searchRequestedRequest)
                // 다른 흐름에서 이미 상태 변경된 요청의 새 작업 없는 멱등 결과 반환
                .orElseGet(() -> MatchingSearchResult.skipped(matchingRequestId));
    }

    // lock 확보 REQUESTED 요청 1건의 만료 여부, 후보 존재 여부, 그룹/제안 생성 판단
    private MatchingSearchResult searchRequestedRequest(MatchingRequest matchingRequest) {
        Instant now = clock.instant();

        // fallback 탐색 만료 시각이 지난 요청의 최종 실패 전환과 이후 재탐색 대상 제외
        if (matchingRequest.isSearchExpired(now)) {
            matchingRequest.failNoAvailableInstructor();
            MatchingStatus matchingStatus = matchingStatusResolver.resolve(
                    matchingRequest,
                    Optional.empty(),
                    Optional.empty()
            );

            // 생성 직후 SEARCHING 이벤트 미발행, 만료 실패 등 이후 상태 변경의 이벤트 경계 전달
            publishAfterCommit(new MatchingRequestStatusChangedEvent(
                    // 이벤트 저장소 도입 전 MVP의 발행 단위 추적용 id 생성
                    UUID.randomUUID(),
                    // 만료 판단과 이벤트 발생 시각의 동일 기준 적용
                    now,
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
            return searching(matchingRequest);
        }

        // 현재 MVP의 강사 프로필 id 오름차순 후보 중 잠금/활성 제안 방어를 통과한 첫 후보 선택
        return selectOfferableCandidate(matchingRequest, candidates)
                .map(candidate -> createGroupAndOffer(matchingRequest, candidate, now))
                // 모든 후보가 다른 트랜잭션에서 변경/점유된 경우 REQUESTED 유지와 다음 재탐색 대기
                .orElseGet(() -> searching(matchingRequest));
    }

    // 후보 존재 요청의 그룹 편입과 강사 제안 생성
    private MatchingSearchResult createGroupAndOffer(
            MatchingRequest matchingRequest,
            OfferableCandidate candidate,
            Instant now
    ) {
        // 현재 요청 포함 후보 그룹 생성과 요청-그룹 연결 row 우선 저장
        MatchingRequestGroup matchingRequestGroup = matchingRequestGroupRepository.save(
                MatchingRequestGroup.createCandidate(candidate.durationMinutes())
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
        createOffer(matchingRequest, matchingRequestGroup, candidate, now);

        return MatchingSearchResult.of(
                matchingRequest,
                MatchingStatus.WAITING_FOR_INSTRUCTOR,
                matchingRequestGroup
        );
    }

    // 기존 그룹에서 강사 거절/만료 이후 후순위 후보 1명에게 새 제안을 순차 노출
    @Transactional
    public NextMatchingOfferResult ensureNextOfferForGroup(
            MatchingRequest matchingRequest,
            MatchingRequestGroup matchingRequestGroup,
            Instant now
    ) {
        MatchingRequestGroup lockedGroup = matchingRequestGroupRepository.findByIdForUpdate(
                        matchingRequestGroup.getId()
                )
                .orElseThrow(() -> new BusinessException(CommonErrorCode.INTERNAL_ERROR));
        List<MatchingOffer> activeOffers = matchingOfferRepository.findByMatchingRequestGroupIdAndStatusForUpdate(
                lockedGroup.getId(),
                MatchingOfferStatus.OFFERED
        );
        if (!activeOffers.isEmpty()) {
            return NextMatchingOfferResult.alreadyActive(activeOffers.getFirst());
        }

        List<InstructorMatchingSetting> candidates = instructorMatchingSettingRepository.findExposedCandidates(
                matchingRequest.getResort(),
                matchingRequest.getSport(),
                matchingRequest.getLessonLevel(),
                matchingRequest.getHeadcount(),
                matchingRequest.getRequestedDurationMinutes(),
                matchingRequest.isEquipmentReady()
        );

        return selectOfferableCandidate(matchingRequest, candidates)
                .map(candidate -> NextMatchingOfferResult.created(createOffer(matchingRequest, lockedGroup, candidate, now)))
                .orElseGet(NextMatchingOfferResult::noCandidate);
    }

    // 후보 목록의 repository 정렬 결과를 유지하되 강사 row 잠금과 활성 제안 방어를 통과한 후보 선택
    private Optional<OfferableCandidate> selectOfferableCandidate(
            MatchingRequest matchingRequest,
            List<InstructorMatchingSetting> candidates
    ) {
        for (InstructorMatchingSetting candidate : candidates) {
            Optional<InstructorMatchingSetting> lockedCandidate = lockStillAvailableCandidate(
                    matchingRequest,
                    candidate
            );

            if (lockedCandidate.isEmpty()) {
                continue;
            }

            InstructorMatchingSetting setting = lockedCandidate.get();
            if (hasOfferedMatchingOffer(setting)) {
                continue;
            }

            if (hasAlreadyOfferedInMatchingRequest(matchingRequest, setting)) {
                continue;
            }

            Optional<Integer> durationMinutes = selectDurationMinutes(matchingRequest, setting);
            if (durationMinutes.isPresent()) {
                return Optional.of(new OfferableCandidate(setting, durationMinutes.get()));
            }
        }

        return Optional.empty();
    }

    // 후보 선정 직전 동일 조건 재조회와 row lock 확보
    private Optional<InstructorMatchingSetting> lockStillAvailableCandidate(
            MatchingRequest matchingRequest,
            InstructorMatchingSetting candidate
    ) {
        return instructorMatchingSettingRepository.findExposedCandidateByIdForUpdate(
                candidate.getId(),
                matchingRequest.getResort(),
                matchingRequest.getSport(),
                matchingRequest.getLessonLevel(),
                matchingRequest.getHeadcount(),
                matchingRequest.getRequestedDurationMinutes(),
                matchingRequest.isEquipmentReady()
        );
    }

    // 강사에게 아직 응답하지 않은 활성 제안 존재 여부 확인
    private boolean hasOfferedMatchingOffer(InstructorMatchingSetting setting) {
        return !matchingOfferRepository.findByInstructorProfileIdAndStatusForUpdate(
                setting.getInstructorProfile().getId(),
                MatchingOfferStatus.OFFERED
        ).isEmpty();
    }

    // 같은 소비자 매칭 요청이 유지되는 동안 이미 제안받은 강사에게 같은 매칭을 다시 노출하지 않음
    private boolean hasAlreadyOfferedInMatchingRequest(
            MatchingRequest matchingRequest,
            InstructorMatchingSetting setting
    ) {
        return matchingOfferRepository.existsByMatchingRequestIdAndInstructorProfileId(
                matchingRequest.getId(),
                setting.getInstructorProfile().getId()
        );
    }

    // 그룹에 활성 제안을 1개 더 만들고 강사 알림 이벤트 발행 지점을 고정
    private MatchingOffer createOffer(
            MatchingRequest matchingRequest,
            MatchingRequestGroup matchingRequestGroup,
            OfferableCandidate candidate,
            Instant now
    ) {
        MatchingOffer matchingOffer = matchingOfferRepository.save(MatchingOffer.create(
                candidate.setting().getInstructorProfile(),
                matchingRequestGroup,
                now,
                now.plus(INSTRUCTOR_RESPONSE_TIMEOUT)
        ));

        // 실제 WebSocket/FCM 부재 상태의 제안 생성 이벤트 포트 전달과 후속 구현 연결 지점
        publishAfterCommit(new MatchingOfferCreatedEvent(
                // 이벤트 저장소 도입 전 MVP의 발행 단위 추적용 id 생성
                UUID.randomUUID(),
                // 제안 row 생성 시각과 이벤트 발생 시각의 동일 기준 적용
                now,
                matchingRequest.getId(),
                matchingRequestGroup.getId(),
                matchingOffer.getId(),
                candidate.durationMinutes(),
                candidate.setting().getInstructorProfile().getId()
        ));

        return matchingOffer;
    }

    // 요청 희망 시간과 강사 가능 시간 교집합 중 서버가 이번 제안에 사용할 최소 분 값 선택
    private Optional<Integer> selectDurationMinutes(
            MatchingRequest matchingRequest,
            InstructorMatchingSetting candidate
    ) {
        return matchingRequest.getRequestedDurationMinutes().stream()
                .filter(candidate::supportsDurationMinutes)
                .min(Integer::compareTo);
    }

    // DB 상태 변경 없는 REQUESTED 요청의 API 표시 상태 SEARCHING 결과 구성
    private MatchingSearchResult searching(MatchingRequest matchingRequest) {
        MatchingStatus matchingStatus = matchingStatusResolver.resolve(
                matchingRequest,
                Optional.empty(),
                Optional.empty()
        );
        return MatchingSearchResult.of(matchingRequest, matchingStatus);
    }

    // DB 변경 커밋 이후 상태 변경 이벤트의 알림 계층 전달
    // 트랜잭션 없는 단위 테스트/직접 호출 환경의 즉시 발행과 같은 코드 경로 검증
    private void publishAfterCommit(MatchingDomainEvent event) {
        // 이벤트 수신 계층의 미구현 상태에서도 Service가 포트 호출 위치를 고정하는 경계
        matchingAfterCommitExecutor.execute(
                "matching-domain-event-publish",
                () -> matchingEventPublisher.publish(event)
        );
    }

    // 잠금/중복제안 방어 이후 실제 제안 생성에 사용할 강사 설정과 확정 강습 시간 묶음
    private record OfferableCandidate(
            InstructorMatchingSetting setting,
            int durationMinutes
    ) {
    }

}
