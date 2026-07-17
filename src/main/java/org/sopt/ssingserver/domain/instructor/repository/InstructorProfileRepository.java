package org.sopt.ssingserver.domain.instructor.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface InstructorProfileRepository extends JpaRepository<InstructorProfile, Long> {

    Optional<InstructorProfile> findByMemberId(Long memberId);

    @EntityGraph(attributePaths = {"member", "resort", "certificateTypes"})
    List<InstructorProfile> findAllByMemberIdIn(Collection<Long> memberIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select profile from InstructorProfile profile where profile.member.id = :memberId")
    Optional<InstructorProfile> findByMemberIdForUpdate(@Param("memberId") Long memberId);
}
