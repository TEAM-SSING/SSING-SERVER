package org.sopt.ssingserver.domain.matching.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface MatchingRequestGroupRepository extends JpaRepository<MatchingRequestGroup, Long> {

    // 강사 응답 처리 중 같은 그룹의 활성 제안이 둘 이상 생기지 않도록 그룹 row 잠금 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("""
            select matchingRequestGroup
            from MatchingRequestGroup matchingRequestGroup
            where matchingRequestGroup.id = :id
            """)
    Optional<MatchingRequestGroup> findByIdForUpdate(@Param("id") Long id);
}
