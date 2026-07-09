package org.sopt.ssingserver.domain.payment.repository;

import java.util.Optional;
import org.sopt.ssingserver.domain.payment.entity.PlatformFeePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformFeePolicyRepository extends JpaRepository<PlatformFeePolicy, Long> {

    Optional<PlatformFeePolicy> findFirstByIsActiveTrueOrderByIdDesc();
}
