package org.sopt.ssingserver.domain.matching.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Optional;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

// 매칭 요청 재탐색 대상 조회와 동시 처리 방어 Repository
public interface MatchingRequestRepository extends JpaRepository<MatchingRequest, Long> {

    // 소비자 매칭 중지와 탐색/상태전이의 같은 요청 row 동시 변경 방지용 비관적 락 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("""
            select matchingRequest
            from MatchingRequest matchingRequest
            where matchingRequest.id = :id
            """)
    Optional<MatchingRequest> findByIdForUpdate(@Param("id") Long id);

    // 커밋 이후 WebSocket 상태 변경 이벤트 수신자 식별용 요청/소비자 조회
    @Query("""
            select matchingRequest
            from MatchingRequest matchingRequest
            join fetch matchingRequest.member
            where matchingRequest.id = :id
            """)
    Optional<MatchingRequest> findRealtimeStatusContextById(@Param("id") Long id);

    // 즉시 트리거와 스케줄러의 같은 REQUESTED 요청 동시 처리 방지용 비관적 락 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("""
            select matchingRequest
            from MatchingRequest matchingRequest
            where matchingRequest.id = :id
              and matchingRequest.status = :status
            """)
    Optional<MatchingRequest> findByIdAndStatusForUpdate(
            @Param("id") Long id,
            @Param("status") MatchingRequestStatus status
    );

    // 주기 스케줄러의 재탐색 대상 REQUESTED 요청 id 오름차순 배치 조회
    @Query("""
            select matchingRequest.id
            from MatchingRequest matchingRequest
            where matchingRequest.status = :status
            order by matchingRequest.id asc
            """)
    List<Long> findIdsByStatusOrderByIdAsc(
            @Param("status") MatchingRequestStatus status,
            Pageable pageable
    );
}
