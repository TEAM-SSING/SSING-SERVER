package org.sopt.ssingserver.domain.matching.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.sopt.ssingserver.domain.matching.dto.result.InstructorMatchingOfferDecisionResult;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "강사 매칭 제안 응답 결과")
public record InstructorMatchingOfferDecisionResponse(

        @Schema(description = "매칭 제안 ID", example = "21")
        Long offerId,

        @Schema(description = "제안 상태", example = "ACCEPTED")
        MatchingOfferStatus offerStatus,

        @Schema(description = "매칭 요청 그룹 ID", example = "3")
        Long groupId,

        @Schema(description = "그룹 상태", example = "INSTRUCTOR_ACCEPTED")
        MatchingRequestGroupStatus groupStatus,

        @Schema(description = "대표 소비자 최종 응답 만료 시각. 수락 시 포함", example = "2026-06-28T15:32:00+09:00")
        Instant requesterConfirmationExpiresAt
) {

    public static InstructorMatchingOfferDecisionResponse from(InstructorMatchingOfferDecisionResult result) {
        return new InstructorMatchingOfferDecisionResponse(
                result.offerId(),
                result.offerStatus(),
                result.groupId(),
                result.groupStatus(),
                result.requesterConfirmationExpiresAt()
        );
    }
}
