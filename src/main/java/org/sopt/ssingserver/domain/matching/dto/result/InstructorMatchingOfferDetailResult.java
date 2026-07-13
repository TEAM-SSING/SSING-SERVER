package org.sopt.ssingserver.domain.matching.dto.result;

import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;

// 강사 홈의 offerId로 I07/I08 매칭 화면을 REST 기준으로 다시 구성하는 상세 조회 결과
public record InstructorMatchingOfferDetailResult(
        Long offerId,
        Long groupId,
        MatchingOfferStatus offerStatus,
        MatchingRequestGroupStatus groupStatus,
        MatchingStatus matchingStatus,
        InstructorMatchingOffersResult.RequestSummaryResult requestSummary,
        InstructorMatchingOffersResult.LessonSummaryResult lessonSummary,
        MatchingPriceSummaryResult priceSummary
) {
}
