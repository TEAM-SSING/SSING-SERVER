package org.sopt.ssingserver.domain.matching.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Optional;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroupItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

// 매칭 요청과 그룹 연결 row 저장 Repository
public interface MatchingRequestGroupItemRepository extends JpaRepository<MatchingRequestGroupItem, Long> {

    Optional<MatchingRequestGroupItem> findFirstByMatchingRequestIdOrderByIdDesc(Long matchingRequestId);

    List<MatchingRequestGroupItem> findByMatchingRequestGroupIdOrderByIdAsc(Long matchingRequestGroupId);

    // 강사 수락/만료로 그룹 안 요청 상태를 함께 바꿀 때 항목 row를 잠금 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("""
            select item
            from MatchingRequestGroupItem item
            where item.matchingRequestGroup.id = :matchingRequestGroupId
            order by item.id asc
            """)
    List<MatchingRequestGroupItem> findByMatchingRequestGroupIdForUpdate(
            @Param("matchingRequestGroupId") Long matchingRequestGroupId
    );
}
