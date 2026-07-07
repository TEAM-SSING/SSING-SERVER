package org.sopt.ssingserver.domain.matching.repository;

import org.sopt.ssingserver.domain.matching.entity.MatchingRequestParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchingRequestParticipantRepository extends JpaRepository<MatchingRequestParticipant, Long> {
}
