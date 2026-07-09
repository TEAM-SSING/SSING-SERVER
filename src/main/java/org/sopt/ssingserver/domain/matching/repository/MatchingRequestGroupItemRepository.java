package org.sopt.ssingserver.domain.matching.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroupItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

// 매칭 요청과 그룹 연결 row 저장 Repository
public interface MatchingRequestGroupItemRepository extends JpaRepository<MatchingRequestGroupItem, Long> {

    Optional<MatchingRequestGroupItem> findFirstByMatchingRequestIdOrderByIdDesc(Long matchingRequestId);

    // 강사 홈 카드 목록의 그룹 요청들을 한 번에 조회함
    @EntityGraph(attributePaths = {
            "matchingRequestGroup",
            "matchingRequest",
            "matchingRequest.member",
            "matchingRequest.resort"
    })
    List<MatchingRequestGroupItem> findByMatchingRequestGroupIdInOrderByMatchingRequestGroupIdAscIdAsc(
            Collection<Long> matchingRequestGroupIds
    );
}
