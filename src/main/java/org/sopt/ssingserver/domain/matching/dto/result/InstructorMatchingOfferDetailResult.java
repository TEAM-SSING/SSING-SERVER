package org.sopt.ssingserver.domain.matching.dto.result;

import java.util.List;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.member.enums.Gender;

// 강사 홈의 offerId로 매칭 화면을 복구할 수 있는지까지 판정한 상세 조회 결과
public sealed interface InstructorMatchingOfferDetailResult permits
        InstructorMatchingOfferDetailResult.Available {

    static InstructorMatchingOfferDetailResult available(
            Long offerId,
            Long groupId,
            MatchingOfferStatus offerStatus,
            MatchingRequestGroupStatus groupStatus,
            MatchingStatus matchingStatus,
            InstructorMatchingOffersResult.RequestSummaryResult requestSummary,
            InstructorMatchingOffersResult.LessonSummaryResult lessonSummary,
            InstructorPriceSummaryResult priceSummary,
            List<ParticipantResult> participants
    ) {
        return new Available(
                offerId,
                groupId,
                offerStatus,
                groupStatus,
                matchingStatus,
                requestSummary,
                lessonSummary,
                priceSummary,
                participants
        );
    }

    record Available(
            Long offerId,
            Long groupId,
            MatchingOfferStatus offerStatus,
            MatchingRequestGroupStatus groupStatus,
            MatchingStatus matchingStatus,
            InstructorMatchingOffersResult.RequestSummaryResult requestSummary,
            InstructorMatchingOffersResult.LessonSummaryResult lessonSummary,
            InstructorPriceSummaryResult priceSummary,
            List<ParticipantResult> participants
    ) implements InstructorMatchingOfferDetailResult {
    }

    public record ParticipantResult(
            int age,
            Gender gender
    ) {
    }
}
