package org.sopt.ssingserver.domain.matching.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.StringToClassMapItem;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.sopt.ssingserver.domain.instructor.enums.InstructorCertificateType;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingStatusQueryResult;
import org.sopt.ssingserver.domain.matching.enums.ConsumerMatchingRecoveryState;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupItemStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.member.enums.Gender;
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
                RequestSummaryResponse.from(result.requestSummary()),
                LessonSummaryResponse.from(result.lessonSummary()),
                MatchingProgressSummaryResponse.from(result.progressSummary()),
                InstructorProfileResponse.from(result.instructorProfile()),
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

            @Schema(
                    description = "Android 화면 전환용 매칭 상태",
                    example = "WAITING_FOR_CONFIRMATION",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            MatchingStatus matchingStatus,

            @Schema(
                    description = "매칭 요청 DB 상태. ACTIVE에서는 REQUESTED, GROUPED, MATCHED 중 하나",
                    example = "MATCHED",
                    allowableValues = {"REQUESTED", "GROUPED", "MATCHED"},
                    requiredMode = Schema.RequiredMode.REQUIRED
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

            @Schema(
                    description = "화면 복구에 필요한 소비자 요청 요약. 모든 ACTIVE 응답에 포함",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            RequestSummaryResponse requestSummary,

            @Schema(description = "최종 확인·결제 화면의 확정 강습 요약. 해당 단계에서만 포함")
            LessonSummaryResponse lessonSummary,

            @Schema(description = "최종 확인 또는 결제 단계의 서버 기준 절대 진행률. 해당 단계에서만 포함")
            MatchingProgressSummaryResponse progressSummary,

            @Schema(description = "최종 확인·결제 화면의 현재 강사 프로필. 해당 단계에서만 포함")
            InstructorProfileResponse instructorProfile,

            @Schema(description = "가격이 필요한 매칭 단계에서 포함되는 제안 시점 가격 요약. 값이 없으면 생략")
            MatchingPriceSummaryResponse priceSummary
    ) implements ConsumerActiveMatchingResponse {
    }

    @Schema(name = "ConsumerActiveMatchingRequestSummary", description = "활성 매칭 요청 화면 요약")
    record RequestSummaryResponse(
            @Schema(description = "리조트 코드와 Android 표시명", requiredMode = Schema.RequiredMode.REQUIRED)
            ResortResponse resort,

            @Schema(description = "요청 종목", example = "SNOWBOARD", requiredMode = Schema.RequiredMode.REQUIRED)
            Sport sport,

            @Schema(description = "요청 강습 수준", example = "FIRST_TIME", requiredMode = Schema.RequiredMode.REQUIRED)
            LessonLevel lessonLevel,

            @Schema(description = "현재 소비자 요청의 수강 인원", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
            int headcount
    ) {

        static RequestSummaryResponse from(MatchingStatusQueryResult.RequestSummaryResult result) {
            if (result == null) {
                return null;
            }

            return new RequestSummaryResponse(
                    ResortResponse.from(result.resort()),
                    result.sport(),
                    result.lessonLevel(),
                    result.headcount()
            );
        }
    }

    @Schema(name = "ConsumerActiveMatchingResort", description = "활성 매칭 요청 리조트 요약")
    record ResortResponse(
            @Schema(description = "리조트 코드", example = "HIGH1", requiredMode = Schema.RequiredMode.REQUIRED)
            String code,

            @Schema(description = "Android 표시용 리조트명", example = "하이원", requiredMode = Schema.RequiredMode.REQUIRED)
            String displayName
    ) {

        static ResortResponse from(MatchingStatusQueryResult.ResortResult result) {
            if (result == null) {
                return null;
            }

            return new ResortResponse(result.code(), result.displayName());
        }
    }

    @Schema(name = "ConsumerActiveMatchingLessonSummary", description = "최종 확인·결제 화면 강습 요약")
    record LessonSummaryResponse(
            @Schema(description = "그룹이 확정한 강습 시간(분)", example = "120", requiredMode = Schema.RequiredMode.REQUIRED)
            int durationMinutes,

            @Schema(description = "그룹 요청별 headcount 합계", example = "4", requiredMode = Schema.RequiredMode.REQUIRED)
            int totalHeadcount,

            @Schema(description = "강습 시작 유형", example = "IMMEDIATE", requiredMode = Schema.RequiredMode.REQUIRED)
            String startType
    ) {

        static LessonSummaryResponse from(MatchingStatusQueryResult.LessonSummaryResult result) {
            if (result == null) {
                return null;
            }

            return new LessonSummaryResponse(
                    result.durationMinutes(),
                    result.totalHeadcount(),
                    result.startType()
            );
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "ConsumerActiveMatchingInstructorProfile", description = "최종 확인·결제 화면 강사 프로필")
    record InstructorProfileResponse(
            @Schema(description = "강사 프로필 ID", example = "7", requiredMode = Schema.RequiredMode.REQUIRED)
            Long instructorId,

            @Schema(description = "강사 표시 이름", example = "김강사", requiredMode = Schema.RequiredMode.REQUIRED)
            String name,

            @Schema(description = "강사 프로필 이미지 URL. 값이 없으면 생략")
            String profileImageUrl,

            @Schema(description = "강사 성별", example = "FEMALE", requiredMode = Schema.RequiredMode.REQUIRED)
            Gender gender,

            @Schema(description = "강사 출생연도", example = "1998", requiredMode = Schema.RequiredMode.REQUIRED)
            Integer birthYear,

            @Schema(description = "강사 프로필 레벨", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
            Integer level,

            @Schema(description = "서울 날짜 기준 만 경력 연수", example = "6", requiredMode = Schema.RequiredMode.REQUIRED)
            Integer careerYears,

            @Schema(description = "완료 상태 강습 수", example = "24", requiredMode = Schema.RequiredMode.REQUIRED)
            Long completedLessonCount,

            @Schema(description = "리뷰 평균 평점. 리뷰가 없으면 생략", example = "4.7")
            Double averageRating,

            @Schema(description = "강사 소개. 값이 없으면 생략")
            String introduction,

            @Schema(description = "보유 자격증 종류. 없으면 빈 배열", requiredMode = Schema.RequiredMode.REQUIRED)
            List<InstructorCertificateType> certificateTypes,

            @Schema(description = "최신 리뷰. 리뷰가 없으면 생략")
            LatestReviewResponse latestReview
    ) {

        static InstructorProfileResponse from(MatchingStatusQueryResult.InstructorProfileResult result) {
            if (result == null) {
                return null;
            }

            return new InstructorProfileResponse(
                    result.instructorId(),
                    result.name(),
                    result.profileImageUrl(),
                    result.gender(),
                    result.birthYear(),
                    result.level(),
                    result.careerYears(),
                    result.completedLessonCount(),
                    result.averageRating(),
                    result.introduction(),
                    result.certificateTypes(),
                    LatestReviewResponse.from(result.latestReview())
            );
        }
    }

    @Schema(name = "ConsumerActiveMatchingLatestReview", description = "활성 매칭 화면 최신 리뷰")
    record LatestReviewResponse(
            @Schema(description = "최신 리뷰 본문", example = "설명을 친절하게 해주셨어요.", requiredMode = Schema.RequiredMode.REQUIRED)
            String content
    ) {

        static LatestReviewResponse from(MatchingStatusQueryResult.LatestReviewResult result) {
            if (result == null) {
                return null;
            }

            return new LatestReviewResponse(result.content());
        }
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
