package org.sopt.ssingserver.domain.matching.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.sopt.ssingserver.domain.matching.dto.result.InstructorMatchingOfferDetailResult;
import org.sopt.ssingserver.domain.matching.enums.InstructorMatchingOfferRecoveryState;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.member.enums.Gender;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
        name = "InstructorMatchingOfferDetailResponse",
        description = "복구 가능한 강사 매칭 제안 상세. 종료된 매칭은 MATCHING_NOT_ACTIVE 오류로 반환합니다."
)
public record InstructorMatchingOfferDetailResponse(
        @Schema(
                description = "강사 제안 상세 복구 상태",
                example = "AVAILABLE",
                allowableValues = "AVAILABLE",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        InstructorMatchingOfferRecoveryState recoveryState,

        @Schema(description = "매칭 제안 ID", example = "21", requiredMode = Schema.RequiredMode.REQUIRED)
        Long offerId,

        @Schema(description = "매칭 요청 그룹 ID", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
        Long groupId,

        @Schema(
                description = "제안 DB 상태. 복구 가능한 상태에서는 OFFERED 또는 ACCEPTED",
                example = "ACCEPTED",
                allowableValues = {"OFFERED", "ACCEPTED"},
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        MatchingOfferStatus offerStatus,

        @Schema(
                description = "매칭 요청 그룹 DB 상태. 복구 가능한 상태에서는 EXPOSED, INSTRUCTOR_ACCEPTED, PAYMENT_PENDING 중 하나",
                example = "INSTRUCTOR_ACCEPTED",
                allowableValues = {"EXPOSED", "INSTRUCTOR_ACCEPTED", "PAYMENT_PENDING"},
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        MatchingRequestGroupStatus groupStatus,

        @Schema(
                description = "강사 매칭 화면 표시 상태. WAITING_FOR_INSTRUCTOR, WAITING_FOR_CONFIRMATION, "
                        + "PAYMENT_PENDING 중 하나",
                example = "WAITING_FOR_CONFIRMATION",
                allowableValues = {"WAITING_FOR_INSTRUCTOR", "WAITING_FOR_CONFIRMATION", "PAYMENT_PENDING"},
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        MatchingStatus matchingStatus,

        @Schema(description = "대표 요청자와 그룹 요청 수 요약", requiredMode = Schema.RequiredMode.REQUIRED)
        InstructorMatchingOffersResponse.RequestSummaryResponse requestSummary,

        @Schema(description = "강습 조건 요약", requiredMode = Schema.RequiredMode.REQUIRED)
        InstructorMatchingOffersResponse.LessonSummaryResponse lessonSummary,

        @Schema(description = "제안 생성 시점에 고정된 강사 자기 정산 금액", requiredMode = Schema.RequiredMode.REQUIRED)
        InstructorPriceSummaryResponse priceSummary,

        @Schema(description = "그룹 전체 강습생 목록(최소 1명). 각 항목은 age와 gender만 반환하며, "
                + "매칭 요청 ID와 참여자 ID 오름차순으로 정렬합니다. 같은 나이와 성별도 서로 다른 참여자면 유지합니다.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<ParticipantResponse> participants
) {

    public static InstructorMatchingOfferDetailResponse from(InstructorMatchingOfferDetailResult result) {
        return switch (result) {
            case InstructorMatchingOfferDetailResult.Available available -> new InstructorMatchingOfferDetailResponse(
                    InstructorMatchingOfferRecoveryState.AVAILABLE,
                    available.offerId(),
                    available.groupId(),
                    available.offerStatus(),
                    available.groupStatus(),
                    available.matchingStatus(),
                    InstructorMatchingOffersResponse.RequestSummaryResponse.from(available.requestSummary()),
                    InstructorMatchingOffersResponse.LessonSummaryResponse.from(available.lessonSummary()),
                    InstructorPriceSummaryResponse.from(available.priceSummary()),
                    available.participants().stream()
                            .map(ParticipantResponse::from)
                            .toList()
            );
        };
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
