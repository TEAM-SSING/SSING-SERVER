package org.sopt.ssingserver.domain.matching.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.StringToClassMapItem;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingStatusQueryResult;
import org.sopt.ssingserver.domain.matching.enums.ConsumerMatchingRecoveryState;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupItemStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.payment.enums.MatchingRequestPaymentStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
        name = "ConsumerActiveMatchingResponse",
        description = "소비자 매칭 복구 응답. recoveryState에 따라 응답 구조가 달라집니다.",
        discriminatorProperty = "recoveryState",
        requiredProperties = "recoveryState",
        properties = @StringToClassMapItem(
                key = "recoveryState",
                value = ConsumerMatchingRecoveryState.class
        ),
        discriminatorMapping = {
                @DiscriminatorMapping(value = "ACTIVE", schema = ConsumerActiveMatchingResponse.Active.class),
                @DiscriminatorMapping(value = "NONE", schema = ConsumerActiveMatchingResponse.None.class)
        },
        oneOf = {
                ConsumerActiveMatchingResponse.Active.class,
                ConsumerActiveMatchingResponse.None.class
        }
)
public sealed interface ConsumerActiveMatchingResponse permits
        ConsumerActiveMatchingResponse.Active,
        ConsumerActiveMatchingResponse.None {

    @Schema(description = "활성 매칭 복구 상태", example = "ACTIVE")
    ConsumerMatchingRecoveryState recoveryState();

    static ConsumerActiveMatchingResponse active(MatchingStatusQueryResult result) {
        return new Active(
                ConsumerMatchingRecoveryState.ACTIVE,
                result.matchingRequestId(),
                result.matchingStatus(),
                result.requestStatus(),
                result.requestStatusReason(),
                result.groupId(),
                result.groupStatus(),
                result.itemStatus(),
                result.offerStatus(),
                result.paymentStatus(),
                MatchingProgressSummaryResponse.from(result.progressSummary()),
                ConsumerMatchingStatusResponse.InstructorProfileResponse.from(result.instructorProfile()),
                MatchingPriceSummaryResponse.from(result.priceSummary())
        );
    }

    static ConsumerActiveMatchingResponse none() {
        return new None(ConsumerMatchingRecoveryState.NONE);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "ConsumerActiveMatchingActive", description = "진행 중인 소비자 매칭 복구 정보")
    record Active(
            @Schema(
                    description = "활성 매칭 복구 상태",
                    example = "ACTIVE",
                    allowableValues = "ACTIVE",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            ConsumerMatchingRecoveryState recoveryState,

            @Schema(description = "매칭 요청 ID", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
            Long matchingRequestId,

            @Schema(description = "Android 화면 전환용 매칭 상태", example = "WAITING_FOR_CONFIRMATION")
            MatchingStatus matchingStatus,

            @Schema(
                    description = "매칭 요청 DB 상태. ACTIVE에서는 REQUESTED, GROUPED, MATCHED 중 하나",
                    example = "MATCHED",
                    allowableValues = {"REQUESTED", "GROUPED", "MATCHED"}
            )
            MatchingRequestStatus requestStatus,

            @Schema(description = "매칭 요청 상태 변경 사유. 사유가 있을 때만 포함", example = "CONSUMER_REJECTED_INSTRUCTOR")
            MatchingRequestStatusReason requestStatusReason,

            @Schema(description = "매칭 요청 그룹 ID. 그룹이 생성된 경우에만 포함", example = "3")
            Long groupId,

            @Schema(description = "매칭 요청 그룹 DB 상태. 그룹이 생성된 경우에만 포함", example = "INSTRUCTOR_ACCEPTED")
            MatchingRequestGroupStatus groupStatus,

            @Schema(description = "그룹 안에서 현재 요청자의 상태. 그룹 항목이 생성된 경우에만 포함", example = "PENDING")
            MatchingRequestGroupItemStatus itemStatus,

            @Schema(description = "강사 제안 상태. 제안이 생성된 경우에만 포함", example = "ACCEPTED")
            MatchingOfferStatus offerStatus,

            @Schema(description = "현재 요청자의 결제 상태. 결제 요청이 생성된 경우에만 포함", example = "PENDING")
            MatchingRequestPaymentStatus paymentStatus,

            @Schema(description = "최종 확인 또는 결제 단계의 서버 기준 절대 진행률. 해당 단계에서만 포함")
            MatchingProgressSummaryResponse progressSummary,

            @Schema(description = "강사가 수락한 뒤 포함되는 강사 프로필 요약. 값이 없으면 응답에서 생략")
            ConsumerMatchingStatusResponse.InstructorProfileResponse instructorProfile,

            @Schema(description = "가격이 필요한 매칭 단계에서 포함되는 제안 시점 가격 요약. 값이 없으면 생략")
            MatchingPriceSummaryResponse priceSummary
    ) implements ConsumerActiveMatchingResponse {
    }

    @Schema(name = "ConsumerActiveMatchingNone", description = "진행 중인 소비자 매칭이 없는 상태")
    record None(
            @Schema(
                    description = "활성 매칭 복구 상태",
                    example = "NONE",
                    allowableValues = "NONE",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            ConsumerMatchingRecoveryState recoveryState
    ) implements ConsumerActiveMatchingResponse {
    }
}
