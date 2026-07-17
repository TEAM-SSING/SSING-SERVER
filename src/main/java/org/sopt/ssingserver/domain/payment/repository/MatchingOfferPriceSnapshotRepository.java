package org.sopt.ssingserver.domain.payment.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.sopt.ssingserver.domain.payment.entity.MatchingOfferPriceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchingOfferPriceSnapshotRepository extends JpaRepository<MatchingOfferPriceSnapshot, Long> {

    Optional<MatchingOfferPriceSnapshot> findByMatchingOfferId(Long matchingOfferId);

    // dev 매칭 조회에서 페이지 안 제안들의 snapshot 누락·중복을 고정 쿼리 수로 진단한다.
    List<MatchingOfferPriceSnapshot> findByMatchingOfferIdIn(Collection<Long> matchingOfferIds);
}
