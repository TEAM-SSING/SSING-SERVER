package org.sopt.ssingserver.domain.instructor.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstructorProfileRepository extends JpaRepository<InstructorProfile, Long> {

    Optional<InstructorProfile> findByMemberId(Long memberId);

    @EntityGraph(attributePaths = "member")
    List<InstructorProfile> findAllByMemberIdIn(Collection<Long> memberIds);
}
