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

    // dev 매칭 조회에서 페이지 안 각 요청의 최신 그룹 연결을 한 번에 읽는다.
    @Query("""
            select item
            from MatchingRequestGroupItem item
            join fetch item.matchingRequestGroup
            join fetch item.matchingRequest matchingRequest
            join fetch matchingRequest.member
            where matchingRequest.id in :matchingRequestIds
              and item.id = (
                  select max(latestItem.id)
                  from MatchingRequestGroupItem latestItem
                  where latestItem.matchingRequest = item.matchingRequest
              )
            order by matchingRequest.id asc
            """)
    List<MatchingRequestGroupItem> findLatestByMatchingRequestIdIn(
            @Param("matchingRequestIds") Collection<Long> matchingRequestIds
    );

    // dev 매칭 조회에서 요청별 전체 그룹 이력을 읽어 동시에 살아있는 그룹이 여러 개인지 진단한다.
    @Query("""
            select item
            from MatchingRequestGroupItem item
            join fetch item.matchingRequestGroup matchingRequestGroup
            join fetch item.matchingRequest matchingRequest
            join fetch matchingRequest.member
            join fetch matchingRequest.resort
            left join fetch matchingRequest.matchingOffer requestOffer
            left join fetch requestOffer.matchingRequestGroup
            left join fetch requestOffer.instructorProfile requestInstructorProfile
            left join fetch requestInstructorProfile.member
            where matchingRequest.id in :matchingRequestIds
            order by matchingRequest.id asc, item.id asc
            """)
    List<MatchingRequestGroupItem> findHistoryByMatchingRequestIdInOrderByRequestIdAscItemIdAsc(
            @Param("matchingRequestIds") Collection<Long> matchingRequestIds
    );

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
            join fetch item.matchingRequest matchingRequest
            join fetch matchingRequest.member
            join fetch matchingRequest.resort
            where item.matchingRequestGroup.id = :matchingRequestGroupId
            order by item.id asc
            """)
    List<MatchingRequestGroupItem> findByMatchingRequestGroupIdOrderByIdAsc(
            @Param("matchingRequestGroupId") Long matchingRequestGroupId
    );

    // 커밋 이후 WebSocket 제안 이벤트 payload 구성용 요청/소비자/리조트 요약 조회
    @Query("""
            select item
            from MatchingRequestGroupItem item
            join fetch item.matchingRequestGroup matchingRequestGroup
            join fetch item.matchingRequest matchingRequest
            join fetch matchingRequest.member
            join fetch matchingRequest.resort
            where matchingRequestGroup.id = :matchingRequestGroupId
            order by item.id asc
            """)
    List<MatchingRequestGroupItem> findRealtimeContextByMatchingRequestGroupIdOrderByIdAsc(
            @Param("matchingRequestGroupId") Long matchingRequestGroupId
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
