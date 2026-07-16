package org.sopt.ssingserver.domain.lesson.dto.result;

import org.sopt.ssingserver.domain.payment.entity.MatchingRequestPriceSnapshot;

public record LessonPriceSummaryResult(
        int lessonPriceAmount,
        int resortPassFeeAmount,
        int totalPaymentAmount
) {

    public static LessonPriceSummaryResult from(MatchingRequestPriceSnapshot snapshot) {
        return new LessonPriceSummaryResult(
                snapshot.getLessonPriceAmount(),
                snapshot.getResortPassFeeAmount(),
                snapshot.getTotalPaymentAmount()
        );
    }
}
