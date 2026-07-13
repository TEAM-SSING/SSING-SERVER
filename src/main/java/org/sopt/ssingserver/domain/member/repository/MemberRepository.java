package org.sopt.ssingserver.domain.member.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

    // 활성 요청이 아직 0건이어도 같은 회원의 동시 생성 요청을 직렬화하기 위한 기준 row 잠금
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("""
            select member
            from Member member
            where member.id = :id
            """)
    Optional<Member> findByIdForUpdate(@Param("id") Long id);
}
