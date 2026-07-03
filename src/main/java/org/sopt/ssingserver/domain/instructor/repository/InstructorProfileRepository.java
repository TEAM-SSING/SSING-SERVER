package org.sopt.ssingserver.domain.instructor.repository;

import java.util.Optional;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstructorProfileRepository extends JpaRepository<InstructorProfile, Long> {

    Optional<InstructorProfile> findByMemberId(Long memberId);
}
