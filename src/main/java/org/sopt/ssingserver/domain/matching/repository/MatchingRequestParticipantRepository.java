package org.sopt.ssingserver.domain.matching.repository;

import java.util.Collection;
import java.util.List;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchingRequestParticipantRepository extends JpaRepository<MatchingRequestParticipant, Long> {

    List<MatchingRequestParticipant> findByMatchingRequestIdOrderByIdAsc(Long matchingRequestId);

    @Query("""
            select participant
            from MatchingRequestParticipant participant
            join fetch participant.matchingRequest matchingRequest
            where matchingRequest.id in :matchingRequestIds
            order by matchingRequest.id asc, participant.id asc
            """)
    List<MatchingRequestParticipant> findByMatchingRequestIdInOrderByMatchingRequestIdAscIdAsc(
            @Param("matchingRequestIds") Collection<Long> matchingRequestIds
    );
}
