package org.sopt.ssingserver.domain.matching.dto.result;

import org.sopt.ssingserver.domain.payment.entity.MatchingOfferPriceSnapshot;
import org.sopt.ssingserver.domain.payment.entity.MatchingRequestPriceSnapshot;

public record MatchingPriceSummaryResult(
        int lessonPriceAmount,
        int resortPassFeeAmount,
        int totalPaymentAmount
) {

    public static MatchingPriceSummaryResult from(MatchingOfferPriceSnapshot snapshot) {
        return new MatchingPriceSummaryResult(
                snapshot.getLessonPriceAmount(),
                snapshot.getResortPassFeeAmount(),
                snapshot.getTotalPaymentAmount()
        );
    }

    public static MatchingPriceSummaryResult from(MatchingRequestPriceSnapshot snapshot) {
        return new MatchingPriceSummaryResult(
                snapshot.getLessonPriceAmount(),
                snapshot.getResortPassFeeAmount(),
                snapshot.getTotalPaymentAmount()
        );
    }
}
