package org.sopt.ssingserver.domain.matching.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.StringToClassMapItem;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
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
        description = "강사 매칭 제안 상세 복구 응답. recoveryState에 따라 응답 구조가 달라집니다.",
        discriminatorProperty = "recoveryState",
        requiredProperties = "recoveryState",
        properties = @StringToClassMapItem(
                key = "recoveryState",
                value = InstructorMatchingOfferRecoveryState.class
        ),
        discriminatorMapping = {
                @DiscriminatorMapping(
                        value = "AVAILABLE",
                        schema = InstructorMatchingOfferDetailResponse.Available.class
                ),
                @DiscriminatorMapping(value = "STALE", schema = InstructorMatchingOfferDetailResponse.Stale.class)
        },
        oneOf = {
                InstructorMatchingOfferDetailResponse.Available.class,
                InstructorMatchingOfferDetailResponse.Stale.class
        }
)
public sealed interface InstructorMatchingOfferDetailResponse permits
        InstructorMatchingOfferDetailResponse.Available,
        InstructorMatchingOfferDetailResponse.Stale {

    @Schema(description = "강사 제안 상세 복구 상태", example = "AVAILABLE")
    InstructorMatchingOfferRecoveryState recoveryState();

    static InstructorMatchingOfferDetailResponse from(InstructorMatchingOfferDetailResult result) {
        return switch (result) {
            case InstructorMatchingOfferDetailResult.Available available -> new Available(
                    InstructorMatchingOfferRecoveryState.AVAILABLE,
                    available.offerId(),
                    available.groupId(),
                    available.offerStatus(),
                    available.groupStatus(),
                    available.matchingStatus(),
                    InstructorMatchingOffersResponse.RequestSummaryResponse.from(available.requestSummary()),
                    InstructorMatchingOffersResponse.LessonSummaryResponse.from(available.lessonSummary()),
                    MatchingPriceSummaryResponse.from(available.priceSummary()),
                    available.participants().stream()
                            .map(ParticipantResponse::from)
                            .toList()
            );
            case InstructorMatchingOfferDetailResult.Stale stale -> new Stale(
                    InstructorMatchingOfferRecoveryState.STALE,
                    stale.offerId()
            );
        };
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(
            name = "InstructorMatchingOfferDetailAvailable",
            description = "복구 가능한 강사 매칭 제안 상세. 허용 상태 조합: "
                    + "OFFERED + EXPOSED + WAITING_FOR_INSTRUCTOR, "
                    + "ACCEPTED + INSTRUCTOR_ACCEPTED + WAITING_FOR_CONFIRMATION, "
                    + "ACCEPTED + PAYMENT_PENDING + PAYMENT_PENDING"
    )
    record Available(
            @Schema(
                    description = "강사 제안 상세 복구 상태",
                    example = "AVAILABLE",
                    allowableValues = "AVAILABLE",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            InstructorMatchingOfferRecoveryState recoveryState,

            @Schema(description = "매칭 제안 ID", example = "21", requiredMode = Schema.RequiredMode.REQUIRED)
            Long offerId,

            @Schema(description = "매칭 요청 그룹 ID", example = "3")
            Long groupId,

            @Schema(
                    description = "제안 DB 상태. AVAILABLE에서는 OFFERED 또는 ACCEPTED",
                    example = "ACCEPTED",
                    allowableValues = {"OFFERED", "ACCEPTED"}
            )
            MatchingOfferStatus offerStatus,

            @Schema(
                    description = "매칭 요청 그룹 DB 상태. AVAILABLE에서는 EXPOSED, INSTRUCTOR_ACCEPTED, PAYMENT_PENDING 중 하나",
                    example = "INSTRUCTOR_ACCEPTED",
                    allowableValues = {"EXPOSED", "INSTRUCTOR_ACCEPTED", "PAYMENT_PENDING"}
            )
            MatchingRequestGroupStatus groupStatus,

            @Schema(
                    description = "강사 매칭 화면 복구용 표시 상태. AVAILABLE에서는 WAITING_FOR_INSTRUCTOR, "
                            + "WAITING_FOR_CONFIRMATION, PAYMENT_PENDING 중 하나",
                    example = "WAITING_FOR_CONFIRMATION",
                    allowableValues = {"WAITING_FOR_INSTRUCTOR", "WAITING_FOR_CONFIRMATION", "PAYMENT_PENDING"}
            )
            MatchingStatus matchingStatus,

            @Schema(description = "대표 요청자와 그룹 요청 수 요약")
            InstructorMatchingOffersResponse.RequestSummaryResponse requestSummary,

            @Schema(description = "강습 조건 요약")
            InstructorMatchingOffersResponse.LessonSummaryResponse lessonSummary,

            @Schema(description = "제안 생성 시점에 고정된 예상 가격")
            MatchingPriceSummaryResponse priceSummary,

            @Schema(description = "그룹 전체 강습생 목록(최소 1명). 각 항목은 age와 gender만 반환하며, "
                    + "매칭 요청 ID와 참여자 ID 오름차순으로 정렬합니다. 같은 나이와 성별도 서로 다른 참여자면 유지합니다.")
            List<ParticipantResponse> participants
    ) implements InstructorMatchingOfferDetailResponse {
    }

    @Schema(name = "InstructorMatchingOfferDetailStale", description = "본인 소유지만 더 이상 복구할 수 없는 매칭 제안")
    record Stale(
            @Schema(
                    description = "강사 제안 상세 복구 상태",
                    example = "STALE",
                    allowableValues = "STALE",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            InstructorMatchingOfferRecoveryState recoveryState,

            @Schema(description = "매칭 제안 ID", example = "21", requiredMode = Schema.RequiredMode.REQUIRED)
            Long offerId
    ) implements InstructorMatchingOfferDetailResponse {
    }

    @Schema(name = "InstructorMatchingOfferParticipant", description = "강사 매칭 제안에 포함된 강습생 정보")
    record ParticipantResponse(
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
