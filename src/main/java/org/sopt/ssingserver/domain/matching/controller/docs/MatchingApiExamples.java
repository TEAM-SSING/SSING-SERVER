package org.sopt.ssingserver.domain.matching.controller.docs;

import java.util.List;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.matching.dto.response.ConsumerActiveMatchingResponse;
import org.sopt.ssingserver.domain.matching.dto.response.InstructorMatchingOfferDetailResponse;
import org.sopt.ssingserver.domain.matching.dto.result.InstructorMatchingOfferDetailResult;
import org.sopt.ssingserver.domain.matching.dto.result.InstructorMatchingOffersResult;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingPriceSummaryResult;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingStatusQueryResult;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.member.enums.Gender;
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
                            "ACTIVE",
                            "진행 중인 매칭 있음",
                            CommonSuccessCode.SUCCESS,
                            ConsumerActiveMatchingResponse.active(activeConsumerMatching())
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

    private static MatchingStatusQueryResult activeConsumerMatching() {
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
                null
        );
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
