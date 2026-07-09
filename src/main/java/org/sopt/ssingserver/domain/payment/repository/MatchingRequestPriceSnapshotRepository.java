package org.sopt.ssingserver.domain.payment.repository;

import org.sopt.ssingserver.domain.payment.entity.MatchingRequestPriceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchingRequestPriceSnapshotRepository extends JpaRepository<MatchingRequestPriceSnapshot, Long> {
}
