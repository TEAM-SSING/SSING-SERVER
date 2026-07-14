package org.sopt.ssingserver.domain.matching.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.sopt.ssingserver.domain.matching.dto.result.InstructorMatchingOfferDetailResult;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.member.enums.Gender;

@Schema(description = "강사 활성 매칭 제안 상세 복구 응답")
public record InstructorMatchingOfferDetailResponse(

        @Schema(description = "매칭 제안 ID", example = "21")
        Long offerId,

        @Schema(description = "매칭 요청 그룹 ID", example = "3")
        Long groupId,

        @Schema(description = "제안 DB 상태", example = "ACCEPTED")
        MatchingOfferStatus offerStatus,

        @Schema(description = "매칭 요청 그룹 DB 상태", example = "INSTRUCTOR_ACCEPTED")
        MatchingRequestGroupStatus groupStatus,

        @Schema(description = "강사 매칭 화면 복구용 표시 상태", example = "WAITING_FOR_CONFIRMATION")
        MatchingStatus matchingStatus,

        @Schema(description = "대표 요청자와 그룹 요청 수 요약")
        InstructorMatchingOffersResponse.RequestSummaryResponse requestSummary,

        @Schema(description = "강습 조건 요약")
        InstructorMatchingOffersResponse.LessonSummaryResponse lessonSummary,

        @Schema(description = "제안 생성 시점에 고정된 예상 가격")
        MatchingPriceSummaryResponse priceSummary,

        @Schema(description = "그룹 전체 강습생의 나이와 성별 목록")
        List<ParticipantResponse> participants
) {

    public static InstructorMatchingOfferDetailResponse from(InstructorMatchingOfferDetailResult result) {
        return new InstructorMatchingOfferDetailResponse(
                result.offerId(),
                result.groupId(),
                result.offerStatus(),
                result.groupStatus(),
                result.matchingStatus(),
                InstructorMatchingOffersResponse.RequestSummaryResponse.from(result.requestSummary()),
                InstructorMatchingOffersResponse.LessonSummaryResponse.from(result.lessonSummary()),
                MatchingPriceSummaryResponse.from(result.priceSummary()),
                result.participants().stream()
                        .map(ParticipantResponse::from)
                        .toList()
        );
    }

    @Schema(name = "InstructorMatchingOfferParticipant", description = "강사 매칭 제안에 포함된 강습생 정보")
    public record ParticipantResponse(

            @Schema(description = "강습생 나이", example = "10")
            int age,

            @Schema(description = "강습생 성별(MALE, FEMALE)", example = "MALE")
            Gender gender
    ) {

        private static ParticipantResponse from(InstructorMatchingOfferDetailResult.ParticipantResult result) {
            return new ParticipantResponse(result.age(), result.gender());
        }
    }
}
