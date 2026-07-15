package org.sopt.ssingserver.domain.matching.controller.docs;

import java.util.List;
import org.sopt.ssingserver.domain.instructor.enums.InstructorCertificateType;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.matching.dto.response.ConsumerActiveMatchingResponse;
import org.sopt.ssingserver.domain.matching.dto.response.InstructorMatchingOfferDetailResponse;
import org.sopt.ssingserver.domain.matching.dto.result.InstructorMatchingOfferDetailResult;
import org.sopt.ssingserver.domain.matching.dto.result.InstructorMatchingOffersResult;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingPriceSummaryResult;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingProgressSummaryResult;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingStatusQueryResult;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupItemStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.member.enums.Gender;
import org.sopt.ssingserver.domain.payment.enums.MatchingRequestPaymentStatus;
import org.sopt.ssingserver.global.response.CommonSuccessCode;
import org.sopt.ssingserver.global.swagger.success.ApiSuccessExampleProvider;
import org.sopt.ssingserver.global.swagger.success.ApiSuccessExampleValue;

final class MatchingApiExamples {

    private MatchingApiExamples() {
    }

    public static final class ConsumerActive implements ApiSuccessExampleProvider {

        @Override
        public List<ApiSuccessExampleValue> examples() {
            return List.of(
                    ApiSuccessExampleValue.success(
                            "SEARCHING",
                            "C03 후보 탐색 중",
                            CommonSuccessCode.SUCCESS,
                            ConsumerActiveMatchingResponse.active(searchingConsumerMatching())
                    ),
                    ApiSuccessExampleValue.success(
                            "WAITING_FOR_TEAM",
                            "C03 팀 결합 대기",
                            CommonSuccessCode.SUCCESS,
                            ConsumerActiveMatchingResponse.active(waitingForTeamConsumerMatching())
                    ),
                    ApiSuccessExampleValue.success(
                            "WAITING_FOR_INSTRUCTOR",
                            "C03 강사 응답 대기",
                            CommonSuccessCode.SUCCESS,
                            ConsumerActiveMatchingResponse.active(waitingForInstructorConsumerMatching())
                    ),
                    ApiSuccessExampleValue.success(
                            "REMATCHING",
                            "C03 다른 강사 재탐색",
                            CommonSuccessCode.SUCCESS,
                            ConsumerActiveMatchingResponse.active(rematchingConsumerMatching())
                    ),
                    ApiSuccessExampleValue.success(
                            "WAITING_FOR_CONFIRMATION",
                            "C05 본인 최종 확인 대기",
                            CommonSuccessCode.SUCCESS,
                            ConsumerActiveMatchingResponse.active(confirmationConsumerMatching(false))
                    ),
                    ApiSuccessExampleValue.success(
                            "WAITING_FOR_OTHER_CONFIRMATIONS",
                            "C05 다른 요청자 최종 확인 대기",
                            CommonSuccessCode.SUCCESS,
                            ConsumerActiveMatchingResponse.active(confirmationConsumerMatching(true))
                    ),
                    ApiSuccessExampleValue.success(
                            "PAYMENT_PENDING",
                            "C06 본인 결제 대기",
                            CommonSuccessCode.SUCCESS,
                            ConsumerActiveMatchingResponse.active(paymentConsumerMatching(false))
                    ),
                    ApiSuccessExampleValue.success(
                            "WAITING_FOR_OTHER_PAYMENTS",
                            "C06 다른 요청자 결제 대기",
                            CommonSuccessCode.SUCCESS,
                            ConsumerActiveMatchingResponse.active(paymentConsumerMatching(true))
                    ),
                    ApiSuccessExampleValue.success(
                            "NONE",
                            "진행 중인 매칭 없음",
                            CommonSuccessCode.SUCCESS,
                            ConsumerActiveMatchingResponse.none()
                    )
            );
        }
    }

    public static final class InstructorOfferDetail implements ApiSuccessExampleProvider {

        @Override
        public List<ApiSuccessExampleValue> examples() {
            return List.of(
                    ApiSuccessExampleValue.success(
                            "AVAILABLE",
                            "복구 가능한 매칭 제안",
                            CommonSuccessCode.SUCCESS,
                            InstructorMatchingOfferDetailResponse.from(availableInstructorOffer())
                    ),
                    ApiSuccessExampleValue.success(
                            "STALE",
                            "본인 소유지만 종료된 매칭 제안",
                            CommonSuccessCode.SUCCESS,
                            InstructorMatchingOfferDetailResponse.from(
                                    InstructorMatchingOfferDetailResult.stale(21L)
                            )
                    )
            );
        }
    }

    private static MatchingStatusQueryResult searchingConsumerMatching() {
        return new MatchingStatusQueryResult(
                10L,
                MatchingStatus.SEARCHING,
                MatchingRequestStatus.REQUESTED,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                consumerRequestSummary(),
                null
        );
    }

    private static MatchingStatusQueryResult waitingForInstructorConsumerMatching() {
        return new MatchingStatusQueryResult(
                10L,
                MatchingStatus.WAITING_FOR_INSTRUCTOR,
                MatchingRequestStatus.GROUPED,
                null,
                3L,
                MatchingRequestGroupStatus.EXPOSED,
                MatchingRequestGroupItemStatus.NOT_REQUESTED,
                MatchingOfferStatus.OFFERED,
                null,
                null,
                null,
                null,
                null,
                null,
                consumerRequestSummary(),
                null
        );
    }

    private static MatchingStatusQueryResult waitingForTeamConsumerMatching() {
        return new MatchingStatusQueryResult(
                10L,
                MatchingStatus.WAITING_FOR_TEAM,
                MatchingRequestStatus.REQUESTED,
                null,
                3L,
                MatchingRequestGroupStatus.CANDIDATE,
                MatchingRequestGroupItemStatus.NOT_REQUESTED,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                consumerRequestSummary(),
                null
        );
    }

    private static MatchingStatusQueryResult rematchingConsumerMatching() {
        return new MatchingStatusQueryResult(
                10L,
                MatchingStatus.REMATCHING,
                MatchingRequestStatus.REQUESTED,
                MatchingRequestStatusReason.CONSUMER_REJECTED_INSTRUCTOR,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                consumerRequestSummary(),
                null
        );
    }

    private static MatchingStatusQueryResult confirmationConsumerMatching(boolean waitingForOthers) {
        return new MatchingStatusQueryResult(
                10L,
                waitingForOthers
                        ? MatchingStatus.WAITING_FOR_OTHER_CONFIRMATIONS
                        : MatchingStatus.WAITING_FOR_CONFIRMATION,
                MatchingRequestStatus.MATCHED,
                null,
                3L,
                MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED,
                waitingForOthers
                        ? MatchingRequestGroupItemStatus.ACCEPTED
                        : MatchingRequestGroupItemStatus.PENDING,
                MatchingOfferStatus.ACCEPTED,
                null,
                MatchingProgressSummaryResult.confirmation(waitingForOthers ? 1 : 0, 2),
                null,
                activeInstructorProfile(),
                null,
                consumerPriceSummary(),
                consumerRequestSummary(),
                consumerLessonSummary()
        );
    }

    private static MatchingStatusQueryResult paymentConsumerMatching(boolean waitingForOthers) {
        return new MatchingStatusQueryResult(
                10L,
                waitingForOthers
                        ? MatchingStatus.WAITING_FOR_OTHER_PAYMENTS
                        : MatchingStatus.PAYMENT_PENDING,
                MatchingRequestStatus.MATCHED,
                null,
                3L,
                MatchingRequestGroupStatus.PAYMENT_PENDING,
                MatchingRequestGroupItemStatus.ACCEPTED,
                MatchingOfferStatus.ACCEPTED,
                waitingForOthers
                        ? MatchingRequestPaymentStatus.COMPLETED
                        : MatchingRequestPaymentStatus.PENDING,
                MatchingProgressSummaryResult.payment(waitingForOthers ? 1 : 0, 2),
                null,
                activeInstructorProfile(),
                null,
                consumerPriceSummary(),
                consumerRequestSummary(),
                consumerLessonSummary()
        );
    }

    private static MatchingStatusQueryResult.RequestSummaryResult consumerRequestSummary() {
        return new MatchingStatusQueryResult.RequestSummaryResult(
                new MatchingStatusQueryResult.ResortResult("HIGH1", "하이원"),
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                2
        );
    }

    private static MatchingStatusQueryResult.LessonSummaryResult consumerLessonSummary() {
        return new MatchingStatusQueryResult.LessonSummaryResult(120, 4, "IMMEDIATE");
    }

    private static MatchingStatusQueryResult.InstructorProfileResult activeInstructorProfile() {
        return new MatchingStatusQueryResult.InstructorProfileResult(
                7L,
                "김강사",
                "https://example.com/instructors/7/profile.jpg",
                Gender.FEMALE,
                1998,
                3,
                6,
                24L,
                4.7,
                "처음 타는 분도 차근차근 알려드려요.",
                List.of(InstructorCertificateType.KSIA_SNOWBOARD_LEVEL_2),
                new MatchingStatusQueryResult.LatestReviewResult("설명을 친절하게 해주셨어요.")
        );
    }

    private static MatchingPriceSummaryResult consumerPriceSummary() {
        return new MatchingPriceSummaryResult(80_000, 20_000, 100_000);
    }

    private static InstructorMatchingOfferDetailResult availableInstructorOffer() {
        return InstructorMatchingOfferDetailResult.available(
                21L,
                3L,
                MatchingOfferStatus.OFFERED,
                MatchingRequestGroupStatus.EXPOSED,
                MatchingStatus.WAITING_FOR_INSTRUCTOR,
                new InstructorMatchingOffersResult.RequestSummaryResult("홍길동", 2, 1),
                new InstructorMatchingOffersResult.LessonSummaryResult(
                        new InstructorMatchingOffersResult.ResortResult("HIGH1", "하이원"),
                        Sport.SNOWBOARD,
                        LessonLevel.FIRST_TIME,
                        120,
                        2,
                        "IMMEDIATE"
                ),
                new MatchingPriceSummaryResult(80_000, 20_000, 100_000),
                List.of(
                        new InstructorMatchingOfferDetailResult.ParticipantResult(10, Gender.MALE),
                        new InstructorMatchingOfferDetailResult.ParticipantResult(12, Gender.FEMALE)
                )
        );
    }
}
