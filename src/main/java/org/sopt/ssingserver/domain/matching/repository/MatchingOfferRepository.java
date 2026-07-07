package org.sopt.ssingserver.domain.matching.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchingOfferRepository extends JpaRepository<MatchingOffer, Long> {

    // 강사별 활성 제안 현재값 확인과 같은 강사 중복 제안 생성을 막기 위한 제안 row 잠금 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select matchingOffer
            from MatchingOffer matchingOffer
            where matchingOffer.instructorProfile.id = :instructorProfileId
              and matchingOffer.status = :status
            """)
    List<MatchingOffer> findByInstructorProfileIdAndStatusForUpdate(
            @Param("instructorProfileId") Long instructorProfileId,
            @Param("status") MatchingOfferStatus status
    );
}
