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
import org.springframework.data.domain.Page;
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

    // 제안 목록 응답에서 그룹 id와 확정 강습 시간을 추가 lazy loading 없이 사용하기 위한 조회
    @Query(
            value = """
                    select matchingOffer
                    from MatchingOffer matchingOffer
                    join fetch matchingOffer.matchingRequestGroup
                    where matchingOffer.instructorProfile.id = :instructorProfileId
                      and matchingOffer.status = :status
                    order by matchingOffer.id asc
                    """,
            countQuery = """
                    select count(matchingOffer)
                    from MatchingOffer matchingOffer
                    where matchingOffer.instructorProfile.id = :instructorProfileId
                      and matchingOffer.status = :status
                    """
    )
    Page<MatchingOffer> findByInstructorProfileIdAndStatusOrderByIdAsc(
            @Param("instructorProfileId") Long instructorProfileId,
            @Param("status") MatchingOfferStatus status,
            Pageable pageable
    );

    // 강사별 활성 제안 현재값 확인과 같은 강사 중복 제안 생성을 막기 위한 제안 row 잠금 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("""
            select matchingOffer
            from MatchingOffer matchingOffer
            where matchingOffer.instructorProfile.id = :instructorProfileId
              and matchingOffer.status = :status
            """)
    List<MatchingOffer> findByInstructorProfileIdAndStatusForUpdate(
            @Param("instructorProfileId") Long instructorProfileId,
            @Param("status") MatchingOfferStatus status
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
