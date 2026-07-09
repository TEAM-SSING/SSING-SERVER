package org.sopt.ssingserver.domain.matching.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroupItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

// 매칭 요청과 그룹 연결 row 저장 Repository
public interface MatchingRequestGroupItemRepository extends JpaRepository<MatchingRequestGroupItem, Long> {

    Optional<MatchingRequestGroupItem> findFirstByMatchingRequestIdOrderByIdDesc(Long matchingRequestId);

    // 강사 홈 카드 목록의 그룹 요청들을 한 번에 조회
    @EntityGraph(attributePaths = {
            "matchingRequestGroup",
            "matchingRequest",
            "matchingRequest.member",
            "matchingRequest.resort"
    })
    List<MatchingRequestGroupItem> findByMatchingRequestGroupIdInOrderByMatchingRequestGroupIdAscIdAsc(
            Collection<Long> matchingRequestGroupIds
    );

    @Query("""
            select item
            from MatchingRequestGroupItem item
            join fetch item.matchingRequest
            where item.matchingRequestGroup.id = :matchingRequestGroupId
            order by item.id asc
            """)
    List<MatchingRequestGroupItem> findByMatchingRequestGroupIdOrderByIdAsc(
            @Param("matchingRequestGroupId") Long matchingRequestGroupId
    );

    // 제안 목록 조회에서 페이지 안 그룹들의 강습 요약을 한 번에 구성하기 위한 배치 조회
    @Query("""
            select item
            from MatchingRequestGroupItem item
            join fetch item.matchingRequestGroup matchingRequestGroup
            join fetch item.matchingRequest matchingRequest
            join fetch matchingRequest.resort
            where matchingRequestGroup.id in :matchingRequestGroupIds
            order by matchingRequestGroup.id asc, item.id asc
            """)
    List<MatchingRequestGroupItem> findByMatchingRequestGroupIdInOrderByGroupIdAscItemIdAsc(
            @Param("matchingRequestGroupIds") Collection<Long> matchingRequestGroupIds
    );

    // 강사 수락/거절로 그룹 안 요청 상태를 함께 바꿀 때 항목 row를 잠금 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("""
            select item
            from MatchingRequestGroupItem item
            join fetch item.matchingRequest
            where item.matchingRequestGroup.id = :matchingRequestGroupId
            order by item.id asc
            """)
    List<MatchingRequestGroupItem> findByMatchingRequestGroupIdForUpdate(
            @Param("matchingRequestGroupId") Long matchingRequestGroupId
    );
}
