package org.sopt.ssingserver.domain.matching.repository;

import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchingRequestRepository extends JpaRepository<MatchingRequest, Long> {
}
