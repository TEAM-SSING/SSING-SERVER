package org.sopt.ssingserver.domain.lesson.controller.docs;

import java.time.OffsetDateTime;
import java.util.List;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.lesson.dto.result.LessonPriceSummaryResult;
import org.sopt.ssingserver.domain.lesson.dto.response.ConsumerLessonDetailResponse;
import org.sopt.ssingserver.domain.lesson.dto.response.InstructorLessonDetailResponse;
import org.sopt.ssingserver.domain.lesson.dto.response.LessonStartConfirmationResponse;
import org.sopt.ssingserver.domain.lesson.response.LessonSuccessCode;
import org.sopt.ssingserver.domain.member.enums.Gender;
import org.sopt.ssingserver.global.response.CommonSuccessCode;
import org.sopt.ssingserver.global.swagger.success.ApiSuccessExampleProvider;
import org.sopt.ssingserver.global.swagger.success.ApiSuccessExampleValue;

final class LessonApiExamples {

    private static final Long LESSON_ID = 9101L;
    private static final Long MATCHING_REQUEST_ID = 91011L;
    private static final Long REPRESENTATIVE_MEMBER_ID = 9001L;
    private static final Long PARTICIPANT_ID = 910111L;

    private LessonApiExamples() {
    }

    public static final class ConsumerDetail implements ApiSuccessExampleProvider {

        @Override
        public List<ApiSuccessExampleValue> examples() {
            return List.of(
                    ApiSuccessExampleValue.success(
                            "CONFIRMED",
                            "시작 전",
                            CommonSuccessCode.SUCCESS,
                            consumerConfirmed()
                    ),
                    ApiSuccessExampleValue.success(
                            "IN_PROGRESS",
                            "진행 중",
                            CommonSuccessCode.SUCCESS,
                            consumerInProgress()
                    ),
                    ApiSuccessExampleValue.success(
                            "COMPLETED",
                            "완료",
                            CommonSuccessCode.SUCCESS,
                            consumerCompleted()
                    ),
                    ApiSuccessExampleValue.success(
                            "CANCELED",
                            "취소",
                            CommonSuccessCode.SUCCESS,
                            consumerCanceled()
                    )
            );
        }
    }

    public static final class InstructorDetail implements ApiSuccessExampleProvider {

        @Override
        public List<ApiSuccessExampleValue> examples() {
            return List.of(
                    ApiSuccessExampleValue.success(
                            "CONFIRMED",
                            "시작 전",
                            CommonSuccessCode.SUCCESS,
                            instructorConfirmed()
                    ),
                    ApiSuccessExampleValue.success(
                            "IN_PROGRESS",
                            "진행 중",
                            CommonSuccessCode.SUCCESS,
                            instructorInProgress()
                    ),
                    ApiSuccessExampleValue.success(
                            "COMPLETED",
                            "완료",
                            CommonSuccessCode.SUCCESS,
                            instructorCompleted()
                    ),
                    ApiSuccessExampleValue.success(
                            "CANCELED",
                            "취소",
                            CommonSuccessCode.SUCCESS,
                            instructorCanceled()
                    )
            );
        }
    }

    public static final class StartConfirmation implements ApiSuccessExampleProvider {

        @Override
        public List<ApiSuccessExampleValue> examples() {
            return List.of(
                    ApiSuccessExampleValue.success(
                            "CONFIRMED_PENDING",
                            "다른 확인 대상 대기",
                            LessonSuccessCode.LESSON_START_CONFIRMATION_PENDING,
                            LessonStartConfirmationResponse.pending(
                                    30L,
                                    new LessonStartConfirmationResponse.StatusInfoResponse(
                                            4,
                                            6,
                                            true,
                                            true
                                    )
                            )
                    ),
                    ApiSuccessExampleValue.success(
                            "IN_PROGRESS_STARTED",
                            "강습 시작",
                            LessonSuccessCode.LESSON_STARTED,
                            LessonStartConfirmationResponse.started(
                                    30L,
                                    OffsetDateTime.parse("2026-06-28T15:31:00+09:00")
                            )
                    )
            );
        }
    }

    private static ConsumerLessonDetailResponse consumerConfirmed() {
        return ConsumerLessonDetailResponse.confirmed(
                LESSON_ID,
                ConsumerLessonDetailResponse.ConfirmedStatusInfoResponse.of(
                        4,
                        6,
                        true,
                        true
                ),
                consumerActiveLessonInfo(),
                consumerInstructorProfile(),
                List.of(consumerConfirmedMatchingRequest())
        );
    }

    private static ConsumerLessonDetailResponse consumerInProgress() {
        return ConsumerLessonDetailResponse.inProgress(
                LESSON_ID,
                ConsumerLessonDetailResponse.InProgressStatusInfoResponse.of(
                        OffsetDateTime.parse("2026-01-01T10:59:00+09:00"),
                        OffsetDateTime.parse("2026-01-01T10:00:00+09:00"),
                        OffsetDateTime.parse("2026-01-01T12:00:00+09:00"),
                        3540,
                        3660
                ),
                consumerActiveLessonInfo(),
                consumerInstructorProfile(),
                List.of(consumerMatchingRequest())
        );
    }

    private static ConsumerLessonDetailResponse consumerCompleted() {
        return ConsumerLessonDetailResponse.completed(
                LESSON_ID,
                ConsumerLessonDetailResponse.CompletedLessonInfoResponse.of(
                        representativeConsumerNames(),
                        5,
                        consumerResort(),
                        Sport.SNOWBOARD,
                        LessonLevel.FIRST_TIME,
                        120,
                        OffsetDateTime.parse("2026-01-01T10:00:00+09:00"),
                        OffsetDateTime.parse("2026-01-01T11:58:00+09:00"),
                        118,
                        consumerPriceSummary()
                ),
                consumerInstructorProfile()
        );
    }

    private static ConsumerLessonDetailResponse consumerCanceled() {
        return ConsumerLessonDetailResponse.canceled(
                LESSON_ID,
                ConsumerLessonDetailResponse.CancelInfoResponse.of(
                        OffsetDateTime.parse("2026-01-01T10:20:00+09:00"),
                        ConsumerLessonDetailResponse.CanceledByResponse.of(
                                REPRESENTATIVE_MEMBER_ID,
                                "김OO"
                        ),
                        "일정 변경"
                ),
                ConsumerLessonDetailResponse.CanceledLessonInfoResponse.of(
                        representativeConsumerNames(),
                        5,
                        consumerResort(),
                        Sport.SNOWBOARD,
                        LessonLevel.FIRST_TIME,
                        120,
                        consumerPriceSummary()
                ),
                consumerInstructorProfile()
        );
    }

    private static ConsumerLessonDetailResponse.LessonInfoResponse consumerActiveLessonInfo() {
        return ConsumerLessonDetailResponse.LessonInfoResponse.of(
                representativeConsumerNames(),
                5,
                consumerResort(),
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                OffsetDateTime.parse("2026-01-01T10:00:00+09:00"),
                120,
                consumerPriceSummary()
        );
    }

    private static LessonPriceSummaryResult consumerPriceSummary() {
        return new LessonPriceSummaryResult(40_000, 25_000, 65_000);
    }

    private static ConsumerLessonDetailResponse.ResortResponse consumerResort() {
        return ConsumerLessonDetailResponse.ResortResponse.of("VIVALDI_PARK", "비발디파크");
    }

    private static ConsumerLessonDetailResponse.InstructorProfileResponse consumerInstructorProfile() {
        return ConsumerLessonDetailResponse.InstructorProfileResponse.of(
                9001L,
                "김씽씽",
                Gender.MALE,
                1994,
                1,
                "https://example.com/instructors/9003.jpg"
        );
    }

    private static ConsumerLessonDetailResponse.ConfirmedMatchingRequestResponse consumerConfirmedMatchingRequest() {
        return ConsumerLessonDetailResponse.ConfirmedMatchingRequestResponse.of(
                MATCHING_REQUEST_ID,
                REPRESENTATIVE_MEMBER_ID,
                "김OO",
                3,
                true,
                List.of(consumerParticipant())
        );
    }

    private static ConsumerLessonDetailResponse.MatchingRequestResponse consumerMatchingRequest() {
        return ConsumerLessonDetailResponse.MatchingRequestResponse.of(
                MATCHING_REQUEST_ID,
                REPRESENTATIVE_MEMBER_ID,
                "김OO",
                3,
                List.of(consumerParticipant())
        );
    }

    private static ConsumerLessonDetailResponse.ParticipantResponse consumerParticipant() {
        return ConsumerLessonDetailResponse.ParticipantResponse.of(
                PARTICIPANT_ID,
                Gender.MALE,
                38
        );
    }

    private static InstructorLessonDetailResponse instructorConfirmed() {
        return InstructorLessonDetailResponse.confirmed(
                LESSON_ID,
                InstructorLessonDetailResponse.ConfirmedStatusInfoResponse.of(
                        4,
                        6,
                        true,
                        true
                ),
                instructorActiveLessonInfo(),
                List.of(instructorConfirmedMatchingRequest())
        );
    }

    private static InstructorLessonDetailResponse instructorInProgress() {
        return InstructorLessonDetailResponse.inProgress(
                LESSON_ID,
                InstructorLessonDetailResponse.InProgressStatusInfoResponse.of(
                        OffsetDateTime.parse("2026-01-01T10:59:00+09:00"),
                        OffsetDateTime.parse("2026-01-01T10:00:00+09:00"),
                        OffsetDateTime.parse("2026-01-01T12:00:00+09:00"),
                        3540,
                        3660
                ),
                instructorActiveLessonInfo(),
                List.of(instructorMatchingRequest())
        );
    }

    private static InstructorLessonDetailResponse instructorCompleted() {
        return InstructorLessonDetailResponse.completed(
                LESSON_ID,
                InstructorLessonDetailResponse.CompletedLessonInfoResponse.of(
                        representativeConsumerNames(),
                        5,
                        instructorResort(),
                        Sport.SNOWBOARD,
                        LessonLevel.FIRST_TIME,
                        120,
                        OffsetDateTime.parse("2026-01-01T10:00:00+09:00"),
                        OffsetDateTime.parse("2026-01-01T11:58:00+09:00"),
                        118,
                        80000
                ),
                List.of(instructorMatchingRequest())
        );
    }

    private static InstructorLessonDetailResponse instructorCanceled() {
        return InstructorLessonDetailResponse.canceled(
                LESSON_ID,
                InstructorLessonDetailResponse.CancelInfoResponse.of(
                        OffsetDateTime.parse("2026-01-01T10:20:00+09:00"),
                        InstructorLessonDetailResponse.CanceledByResponse.of(
                                REPRESENTATIVE_MEMBER_ID,
                                "김OO"
                        ),
                        "일정 변경"
                ),
                InstructorLessonDetailResponse.CanceledLessonInfoResponse.of(
                        representativeConsumerNames(),
                        5,
                        instructorResort(),
                        Sport.SNOWBOARD,
                        LessonLevel.FIRST_TIME,
                        120,
                        80000
                ),
                List.of(instructorMatchingRequest())
        );
    }

    private static InstructorLessonDetailResponse.LessonInfoResponse instructorActiveLessonInfo() {
        return InstructorLessonDetailResponse.LessonInfoResponse.of(
                representativeConsumerNames(),
                5,
                instructorResort(),
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                OffsetDateTime.parse("2026-01-01T10:00:00+09:00"),
                120,
                80000
        );
    }

    private static InstructorLessonDetailResponse.ResortResponse instructorResort() {
        return InstructorLessonDetailResponse.ResortResponse.of("HIGH1", "하이원");
    }

    private static InstructorLessonDetailResponse.ConfirmedMatchingRequestResponse instructorConfirmedMatchingRequest() {
        return InstructorLessonDetailResponse.ConfirmedMatchingRequestResponse.of(
                MATCHING_REQUEST_ID,
                REPRESENTATIVE_MEMBER_ID,
                "김OO",
                3,
                40000,
                true,
                List.of(instructorParticipant())
        );
    }

    private static InstructorLessonDetailResponse.MatchingRequestResponse instructorMatchingRequest() {
        return InstructorLessonDetailResponse.MatchingRequestResponse.of(
                MATCHING_REQUEST_ID,
                REPRESENTATIVE_MEMBER_ID,
                "김OO",
                3,
                40000,
                List.of(instructorParticipant())
        );
    }

    private static InstructorLessonDetailResponse.ParticipantResponse instructorParticipant() {
        return InstructorLessonDetailResponse.ParticipantResponse.of(
                PARTICIPANT_ID,
                Gender.MALE,
                38
        );
    }

    private static List<String> representativeConsumerNames() {
        return List.of("김OO", "홍지민");
    }
}
