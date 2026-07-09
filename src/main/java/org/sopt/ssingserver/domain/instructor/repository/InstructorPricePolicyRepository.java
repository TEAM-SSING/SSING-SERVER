package org.sopt.ssingserver.domain.instructor.repository;

import java.util.Optional;
import org.sopt.ssingserver.domain.instructor.entity.InstructorPricePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstructorPricePolicyRepository extends JpaRepository<InstructorPricePolicy, Long> {

    Optional<InstructorPricePolicy> findFirstByInstructorProfileIdAndIsActiveTrueOrderByIdDesc(
            Long instructorProfileId
    );
}
