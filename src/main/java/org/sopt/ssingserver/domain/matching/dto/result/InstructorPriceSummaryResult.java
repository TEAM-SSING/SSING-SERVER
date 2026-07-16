package org.sopt.ssingserver.domain.matching.dto.result;

import org.sopt.ssingserver.domain.payment.entity.MatchingOfferPriceSnapshot;

public record InstructorPriceSummaryResult(
        int instructorSettlementAmount
) {

    public static InstructorPriceSummaryResult from(MatchingOfferPriceSnapshot snapshot) {
        return new InstructorPriceSummaryResult(snapshot.getInstructorSettlementAmount());
    }
}
