package org.sopt.ssingserver.domain.payment.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.sopt.ssingserver.domain.payment.entity.MatchingOfferPriceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchingOfferPriceSnapshotRepository extends JpaRepository<MatchingOfferPriceSnapshot, Long> {

    Optional<MatchingOfferPriceSnapshot> findByMatchingOfferId(Long matchingOfferId);

    List<MatchingOfferPriceSnapshot> findByMatchingOfferIdIn(Collection<Long> matchingOfferIds);
}
