package org.sopt.ssingserver.domain.matching.dto.result;

import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;

public record NextMatchingOfferResult(
        Status status,
        MatchingOffer matchingOffer
) {

    public static NextMatchingOfferResult created(MatchingOffer matchingOffer) {
        return new NextMatchingOfferResult(Status.CREATED, matchingOffer);
    }

    public static NextMatchingOfferResult alreadyActive(MatchingOffer matchingOffer) {
        return new NextMatchingOfferResult(Status.ALREADY_ACTIVE, matchingOffer);
    }

    public static NextMatchingOfferResult noCandidate() {
        return new NextMatchingOfferResult(Status.NO_CANDIDATE, null);
    }

    public boolean hasActiveOffer() {
        return status != Status.NO_CANDIDATE;
    }

    public enum Status {
        CREATED,
        ALREADY_ACTIVE,
        NO_CANDIDATE
    }
}
