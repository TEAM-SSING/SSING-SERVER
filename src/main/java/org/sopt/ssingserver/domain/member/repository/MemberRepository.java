package org.sopt.ssingserver.domain.member.repository;

import org.sopt.ssingserver.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
}
