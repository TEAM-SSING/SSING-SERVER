package org.sopt.ssingserver.domain.matching.repository;

import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroupItem;
import org.springframework.data.jpa.repository.JpaRepository;

// 매칭 요청과 그룹 연결 row 저장 Repository
public interface MatchingRequestGroupItemRepository extends JpaRepository<MatchingRequestGroupItem, Long> {
}
