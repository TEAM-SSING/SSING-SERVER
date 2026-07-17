package org.sopt.ssingserver.domain.instructor.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.sopt.ssingserver.domain.instructor.entity.InstructorPricePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface InstructorPricePolicyRepository extends JpaRepository<InstructorPricePolicy, Long> {

    Optional<InstructorPricePolicy> findFirstByInstructorProfileIdAndIsActiveTrueOrderByIdDesc(
            Long instructorProfileId
    );

    List<InstructorPricePolicy> findAllByInstructorProfileIdInAndIsActiveTrueOrderByInstructorProfileIdAscIdDesc(
            Collection<Long> instructorProfileIds
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("""
            select pricePolicy
            from InstructorPricePolicy pricePolicy
            where pricePolicy.instructorProfile.id = :instructorProfileId
              and pricePolicy.isActive = true
            order by pricePolicy.id desc
            """)
    List<InstructorPricePolicy> findActiveByInstructorProfileIdForUpdate(
            @Param("instructorProfileId") Long instructorProfileId
    );
}
