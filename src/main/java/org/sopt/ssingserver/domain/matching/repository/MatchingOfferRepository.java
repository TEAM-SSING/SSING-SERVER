package org.sopt.ssingserver.domain.matching.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface MatchingOfferRepository extends JpaRepository<MatchingOffer, Long> {

    // 강사 응답 처리 시 같은 제안을 동시에 수락/거절하지 못하게 제안 row 잠금 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("""
            select matchingOffer
            from MatchingOffer matchingOffer
            where matchingOffer.id = :id
            """)
    Optional<MatchingOffer> findByIdForUpdate(@Param("id") Long id);

    // 커밋 이후 WebSocket 제안 이벤트 payload 구성용 강사/그룹 요약 조회
    @Query("""
            select matchingOffer
            from MatchingOffer matchingOffer
            join fetch matchingOffer.instructorProfile instructorProfile
            join fetch instructorProfile.member
            join fetch matchingOffer.matchingRequestGroup
            where matchingOffer.id = :id
            """)
    Optional<MatchingOffer> findRealtimeContextById(@Param("id") Long id);

    // 강사 제안 상세 복구에서 소유자 확인과 그룹 상태를 추가 lazy loading 없이 읽기 위한 조회
    @Query("""
            select matchingOffer
            from MatchingOffer matchingOffer
            join fetch matchingOffer.instructorProfile
            join fetch matchingOffer.matchingRequestGroup
            where matchingOffer.id = :id
            """)
    Optional<MatchingOffer> findDetailById(@Param("id") Long id);

    // 홈의 ID 없는 MATCHING 카드 진입 시 복구 가능한 실시간 제안 ID를 최대 2건만 확인한다.
    // 정상 카디널리티는 0~1건이며, Service가 2건을 불변식 위반으로 처리한다.
    @Query("""
            select matchingOffer.id
            from MatchingOffer matchingOffer
            join matchingOffer.matchingRequestGroup matchingRequestGroup
            where matchingOffer.instructorProfile.id = :instructorProfileId
              and matchingOffer.status = :status
              and matchingRequestGroup.status = :groupStatus
            order by matchingOffer.id asc
            """)
    List<Long> findIdsByInstructorProfileIdAndStatusAndGroupStatusOrderByIdAsc(
            @Param("instructorProfileId") Long instructorProfileId,
            @Param("status") MatchingOfferStatus status,
            @Param("groupStatus") MatchingRequestGroupStatus groupStatus,
            Pageable pageable
    );

    // 강사별 live 협상 존재 여부 확인. 후보 선택은 matching setting row lock과 READ_COMMITTED 트랜잭션을 함께 쓴다.
    // offer row까지 잠그면 강사 응답 경로의 offer -> setting 순서와 충돌할 수 있어 존재 확인은 잠그지 않는다.
    @Query("""
            select case when count(matchingOffer) > 0 then true else false end
            from MatchingOffer matchingOffer
            join matchingOffer.matchingRequestGroup matchingRequestGroup
            where matchingOffer.instructorProfile.id = :instructorProfileId
              and (
                  matchingOffer.status = :offeredStatus
                  or (
                      matchingOffer.status = :acceptedStatus
                      and matchingRequestGroup.status in :acceptedGroupStatuses
                  )
              )
            """)
    boolean existsActiveByInstructorProfileId(
            @Param("instructorProfileId") Long instructorProfileId,
            @Param("offeredStatus") MatchingOfferStatus offeredStatus,
            @Param("acceptedStatus") MatchingOfferStatus acceptedStatus,
            @Param("acceptedGroupStatuses") Collection<MatchingRequestGroupStatus> acceptedGroupStatuses
    );

    // 그룹 단위 순차 노출에서 이미 대기 중인 활성 제안이 있는지 확인하기 위한 잠금 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("""
            select matchingOffer
            from MatchingOffer matchingOffer
            where matchingOffer.matchingRequestGroup.id = :matchingRequestGroupId
              and matchingOffer.status = :status
            order by matchingOffer.id asc
            """)
    List<MatchingOffer> findByMatchingRequestGroupIdAndStatusForUpdate(
            @Param("matchingRequestGroupId") Long matchingRequestGroupId,
            @Param("status") MatchingOfferStatus status
    );

    // 유한 응답 시간 정책 재도입 시 만료 대상 OFFERED 제안 id 배치 조회
    @Query("""
            select matchingOffer.id
            from MatchingOffer matchingOffer
            where matchingOffer.status = :status
              and matchingOffer.expiresAt is not null
              and matchingOffer.expiresAt <= :now
            order by matchingOffer.id asc
            """)
    List<Long> findIdsByStatusAndExpiresAtLessThanEqualOrderByIdAsc(
            @Param("status") MatchingOfferStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );

    Optional<MatchingOffer> findFirstByMatchingRequestGroupIdOrderByIdDesc(Long matchingRequestGroupId);

    // dev 매칭 조회에서 현재 그룹들의 제안 이력을 고정 쿼리 수로 읽는다.
    @Query("""
            select matchingOffer
            from MatchingOffer matchingOffer
            join fetch matchingOffer.matchingRequestGroup matchingRequestGroup
            join fetch matchingOffer.instructorProfile instructorProfile
            join fetch instructorProfile.member
            where matchingRequestGroup.id in :matchingRequestGroupIds
            order by matchingRequestGroup.id asc, matchingOffer.id desc
            """)
    List<MatchingOffer> findByMatchingRequestGroupIdInOrderByGroupIdAscOfferIdDesc(
            @Param("matchingRequestGroupIds") Collection<Long> matchingRequestGroupIds
    );

    // dev 매칭 조회에서 강사가 서로 다른 그룹의 live 협상에 동시에 연결됐는지 한 번에 확인한다.
    @Query("""
            select matchingOffer
            from MatchingOffer matchingOffer
            join fetch matchingOffer.matchingRequestGroup matchingRequestGroup
            join fetch matchingOffer.instructorProfile instructorProfile
            join fetch instructorProfile.member
            where instructorProfile.id in :instructorProfileIds
              and (
                  matchingOffer.status = :offeredStatus
                  or (
                      matchingOffer.status = :acceptedStatus
                      and matchingRequestGroup.status in :acceptedGroupStatuses
                  )
              )
            order by instructorProfile.id asc, matchingRequestGroup.id asc, matchingOffer.id asc
            """)
    List<MatchingOffer> findActiveByInstructorProfileIdIn(
            @Param("instructorProfileIds") Collection<Long> instructorProfileIds,
            @Param("offeredStatus") MatchingOfferStatus offeredStatus,
            @Param("acceptedStatus") MatchingOfferStatus acceptedStatus,
            @Param("acceptedGroupStatuses") Collection<MatchingRequestGroupStatus> acceptedGroupStatuses
    );

    // 같은 소비자 매칭 요청이 살아있는 동안 이미 제안을 받은 강사를 다시 후보로 고르지 않기 위한 이력 확인
    @Query("""
            select case when count(matchingOffer) > 0 then true else false end
            from MatchingOffer matchingOffer
            where matchingOffer.instructorProfile.id = :instructorProfileId
              and exists (
                  select 1
                  from MatchingRequestGroupItem item
                  where item.matchingRequestGroup = matchingOffer.matchingRequestGroup
                    and item.matchingRequest.id = :matchingRequestId
              )
            """)
    boolean existsByMatchingRequestIdAndInstructorProfileId(
            @Param("matchingRequestId") Long matchingRequestId,
            @Param("instructorProfileId") Long instructorProfileId
    );

    // 매칭 중지 시 현재 그룹의 활성 제안을 같은 트랜잭션에서 종료하기 위한 잠금 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("""
            select matchingOffer
            from MatchingOffer matchingOffer
            where matchingOffer.matchingRequestGroup.id = :matchingRequestGroupId
              and matchingOffer.status in :statuses
            """)
    List<MatchingOffer> findByMatchingRequestGroupIdAndStatusIn(
            @Param("matchingRequestGroupId") Long matchingRequestGroupId,
            @Param("statuses") Collection<MatchingOfferStatus> statuses
    );

    // 강사 홈에 노출할 응답 대기/수락 이후 진행 중 제안 조회
    @Query("""
            select matchingOffer
            from MatchingOffer matchingOffer
            join fetch matchingOffer.matchingRequestGroup matchingRequestGroup
            where matchingOffer.instructorProfile.id = :instructorProfileId
              and (
                  matchingOffer.status = :offeredStatus
                  or (
                      matchingOffer.status = :acceptedStatus
                      and matchingRequestGroup.status in :acceptedGroupStatuses
                  )
              )
            order by matchingOffer.exposedAt desc, matchingOffer.id desc
            """)
    List<MatchingOffer> findInstructorHomeOffers(
            @Param("instructorProfileId") Long instructorProfileId,
            @Param("offeredStatus") MatchingOfferStatus offeredStatus,
            @Param("acceptedStatus") MatchingOfferStatus acceptedStatus,
            @Param("acceptedGroupStatuses") Collection<MatchingRequestGroupStatus> acceptedGroupStatuses
    );
}
