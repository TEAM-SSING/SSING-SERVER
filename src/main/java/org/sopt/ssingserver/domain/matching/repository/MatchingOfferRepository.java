package org.sopt.ssingserver.domain.matching.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface MatchingOfferRepository extends JpaRepository<MatchingOffer, Long> {

    // 강사별 활성 제안 현재값 확인과 같은 강사 중복 제안 생성을 막기 위한 제안 row 잠금 조회함
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

    Optional<MatchingOffer> findFirstByMatchingRequestGroupIdOrderByIdDesc(Long matchingRequestGroupId);

    // 매칭 중지 시 현재 그룹의 활성 제안을 같은 트랜잭션에서 종료하기 위한 잠금 조회함
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

    // 강사 홈에 노출할 응답 대기/수락 이후 진행 중 제안 조회함
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
