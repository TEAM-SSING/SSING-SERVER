package org.sopt.ssingserver.domain.matching.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.instructor.enums.InstructorCertificateType;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingStatusQueryResult;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingPriceSummaryResult;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingProgressSummaryResult;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupItemStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.Gender;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class ConsumerMatchingStatusResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void activeRecovery는_ACTIVE와_화면블록을_반환하고_ID조회전용필드는_제외한다() throws Exception {
        MatchingStatusQueryResult result = new MatchingStatusQueryResult(
                10L,
                MatchingStatus.WAITING_FOR_CONFIRMATION,
                MatchingRequestStatus.MATCHED,
                null,
                20L,
                MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED,
                MatchingRequestGroupItemStatus.PENDING,
                MatchingOfferStatus.ACCEPTED,
                null,
                MatchingProgressSummaryResult.confirmation(1, 2),
                Instant.parse("2026-07-07T00:10:00Z"),
                new MatchingStatusQueryResult.InstructorProfileResult(
                        40L,
                        "김강사",
                        "https://example.com/instructor.png",
                        Gender.FEMALE,
                        1998,
                        3,
                        6,
                        24L,
                        4.7,
                        "친절한 강사입니다.",
                        List.of(InstructorCertificateType.KSIA_SNOWBOARD_LEVEL_2),
                        new MatchingStatusQueryResult.LatestReviewResult("설명을 친절하게 해주셨어요.")
                ),
                30L,
                priceSummary(),
                requestSummary(),
                new MatchingStatusQueryResult.LessonSummaryResult(120, 4, "IMMEDIATE")
        );

        String json = objectMapper.writeValueAsString(ConsumerActiveMatchingResponse.active(result));

        assertThat(json).contains("\"recoveryState\":\"ACTIVE\"");
        assertThat(json).contains("\"matchingRequestId\":10");
        assertThat(json).contains("\"matchingStatus\":\"WAITING_FOR_CONFIRMATION\"");
        assertThat(json).contains("\"requestSummary\":{\"resort\":{\"code\":\"HIGH1\",\"displayName\":\"하이원\"}");
        assertThat(json).contains("\"headcount\":2");
        var requestSummary = objectMapper.readTree(json).path("requestSummary");
        assertThat(requestSummary.path("requesterName").asString()).isEqualTo("요청자");
        assertThat(requestSummary.path("participants").size()).isEqualTo(2);
        assertThat(requestSummary.path("participants").path(0).path("name").asString()).isEqualTo("홍길동");
        assertThat(requestSummary.path("participants").path(0).path("age").asInt()).isEqualTo(24);
        assertThat(requestSummary.path("participants").path(0).path("gender").asString()).isEqualTo("FEMALE");
        assertThat(requestSummary.path("participants").path(1).has("name")).isFalse();
        assertThat(requestSummary.path("participants").path(1).path("age").asInt()).isEqualTo(30);
        assertThat(requestSummary.path("participants").path(1).path("gender").asString()).isEqualTo("MALE");
        assertThat(json).contains("\"lessonSummary\":{\"durationMinutes\":120,\"totalHeadcount\":4,\"startType\":\"IMMEDIATE\"}");
        assertThat(json).contains("\"careerYears\":6");
        assertThat(json).contains("\"completedLessonCount\":24");
        assertThat(json).contains("\"averageRating\":4.7");
        assertThat(json).contains("\"certificateTypes\":[\"KSIA_SNOWBOARD_LEVEL_2\"]");
        assertThat(json).contains("\"latestReview\":{\"content\":\"설명을 친절하게 해주셨어요.\"}");
        assertThat(json).doesNotContain("phone");
        assertThat(json).doesNotContain("payload", "expiresAt", "lessonId");
    }

    @Test
    void activeRecovery는_활성요청이_없으면_NONE만_반환한다() throws Exception {
        String json = objectMapper.writeValueAsString(ConsumerActiveMatchingResponse.none());

        assertThat(json).isEqualTo("{\"recoveryState\":\"NONE\"}");
    }

    @Test
    void from은_SEARCHING_응답에서_null_선택필드를_JSON에서_제외한다() throws Exception {
        MatchingStatusQueryResult result = new MatchingStatusQueryResult(
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

        ConsumerMatchingStatusResponse response = ConsumerMatchingStatusResponse.from(result);
        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"matchingRequestId\":10");
        assertThat(json).contains("\"matchingStatus\":\"SEARCHING\"");
        assertThat(json).contains("\"requestStatus\":\"REQUESTED\"");
        assertThat(json).doesNotContain("groupId");
        assertThat(json).doesNotContain("offerStatus");
        assertThat(json).doesNotContain("instructorProfile");
        assertThat(json).doesNotContain("lessonId");
        assertThat(json).doesNotContain("priceSummary");
        assertThat(json).doesNotContain("progressSummary");
    }

    @Test
    void from은_강사프로필_등급값이_있으면_level까지_JSON에_포함한다() throws Exception {
        MatchingStatusQueryResult result = new MatchingStatusQueryResult(
                10L,
                MatchingStatus.WAITING_FOR_CONFIRMATION,
                MatchingRequestStatus.MATCHED,
                null,
                20L,
                MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED,
                MatchingRequestGroupItemStatus.PENDING,
                MatchingOfferStatus.ACCEPTED,
                null,
                MatchingProgressSummaryResult.confirmation(1, 2),
                Instant.parse("2026-07-07T00:10:00Z"),
                new MatchingStatusQueryResult.InstructorProfileResult(
                        40L,
                        "김강사",
                        "https://example.com/instructor.png",
                        Gender.FEMALE,
                        1998,
                        3
                ),
                null,
                priceSummary()
        );

        ConsumerMatchingStatusResponse response = ConsumerMatchingStatusResponse.from(result);
        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"groupId\":20");
        assertThat(json).contains("\"groupStatus\":\"INSTRUCTOR_ACCEPTED\"");
        assertThat(json).contains("\"itemStatus\":\"PENDING\"");
        assertThat(json).contains("\"offerStatus\":\"ACCEPTED\"");
        assertThat(json).contains("\"instructorId\":40");
        assertThat(json).contains("\"name\":\"김강사\"");
        assertThat(json).contains("\"profileImageUrl\":\"https://example.com/instructor.png\"");
        assertThat(json).contains("\"birthYear\":1998");
        assertThat(json).contains("\"level\":3");
        assertThat(json).contains("\"acceptedRequesterCount\":1");
        assertThat(json).contains("\"totalRequesterCount\":2");
        assertThat(json).doesNotContain("paidRequesterCount");
        assertThat(json).contains("\"lessonPriceAmount\":80000");
        assertThat(json).contains("\"resortPassFeeAmount\":20000");
        assertThat(json).contains("\"totalPaymentAmount\":100000");
    }

    @Test
    void from은_강사프로필_factory_경로에서_저장된_level을_JSON에_포함한다() throws Exception {
        MatchingStatusQueryResult.InstructorProfileResult instructorProfile =
                MatchingStatusQueryResult.InstructorProfileResult.from(approvedInstructorProfile(40L, 2));
        MatchingStatusQueryResult result = new MatchingStatusQueryResult(
                10L,
                MatchingStatus.WAITING_FOR_CONFIRMATION,
                MatchingRequestStatus.MATCHED,
                null,
                20L,
                MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED,
                MatchingRequestGroupItemStatus.PENDING,
                MatchingOfferStatus.ACCEPTED,
                null,
                null,
                Instant.parse("2026-07-07T00:10:00Z"),
                instructorProfile,
                null,
                priceSummary()
        );

        ConsumerMatchingStatusResponse response = ConsumerMatchingStatusResponse.from(result);
        String json = objectMapper.writeValueAsString(response);

        assertThat(instructorProfile.level()).isEqualTo(2);
        assertThat(json).contains("\"instructorId\":40");
        assertThat(json).contains("\"name\":\"김강사\"");
        assertThat(json).contains("\"profileImageUrl\":\"https://example.com/instructor.png\"");
        assertThat(json).contains("\"gender\":\"FEMALE\"");
        assertThat(json).contains("\"birthYear\":1998");
        assertThat(json).contains("\"level\":2");
        assertThat(json).doesNotContain("certificate");
    }

    @Test
    void ID조회응답은_Active전용_강사상세필드를_직렬화하지_않는다() throws Exception {
        MatchingStatusQueryResult.InstructorProfileResult instructorProfile =
                new MatchingStatusQueryResult.InstructorProfileResult(
                        40L,
                        "김강사",
                        null,
                        Gender.FEMALE,
                        1998,
                        3,
                        6,
                        24L,
                        4.7,
                        "친절한 강사입니다.",
                        List.of(InstructorCertificateType.KSIA_SNOWBOARD_LEVEL_2),
                        new MatchingStatusQueryResult.LatestReviewResult("최신 리뷰")
                );
        MatchingStatusQueryResult result = new MatchingStatusQueryResult(
                10L,
                MatchingStatus.WAITING_FOR_CONFIRMATION,
                MatchingRequestStatus.MATCHED,
                null,
                20L,
                MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED,
                MatchingRequestGroupItemStatus.PENDING,
                MatchingOfferStatus.ACCEPTED,
                null,
                null,
                null,
                instructorProfile,
                null,
                priceSummary(),
                requestSummary(),
                new MatchingStatusQueryResult.LessonSummaryResult(120, 2, "IMMEDIATE")
        );

        String json = objectMapper.writeValueAsString(ConsumerMatchingStatusResponse.from(result));

        assertThat(json).contains("\"instructorId\":40", "\"level\":3");
        assertThat(json).doesNotContain(
                "requestSummary",
                "lessonSummary",
                "careerYears",
                "completedLessonCount",
                "averageRating",
                "introduction",
                "certificateTypes",
                "latestReview"
        );
    }

    @Test
    void from은_강사프로필_level이_null이면_JSON에서_제외한다() throws Exception {
        MatchingStatusQueryResult result = new MatchingStatusQueryResult(
                10L,
                MatchingStatus.WAITING_FOR_CONFIRMATION,
                MatchingRequestStatus.MATCHED,
                null,
                20L,
                MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED,
                MatchingRequestGroupItemStatus.PENDING,
                MatchingOfferStatus.ACCEPTED,
                null,
                null,
                null,
                new MatchingStatusQueryResult.InstructorProfileResult(
                        40L,
                        "김강사",
                        null,
                        Gender.FEMALE,
                        1998,
                        null
                ),
                null,
                priceSummary()
        );

        String json = objectMapper.writeValueAsString(ConsumerMatchingStatusResponse.from(result));

        assertThat(json).contains("\"instructorId\":40");
        assertThat(json).doesNotContain("\"level\"");
    }

    private MatchingPriceSummaryResult priceSummary() {
        return new MatchingPriceSummaryResult(80_000, 20_000, 100_000);
    }

    private MatchingStatusQueryResult.RequestSummaryResult requestSummary() {
        return new MatchingStatusQueryResult.RequestSummaryResult(
                new MatchingStatusQueryResult.ResortResult("HIGH1", "하이원"),
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                2,
                "요청자",
                List.of(
                        new MatchingStatusQueryResult.ParticipantResult("홍길동", 24, Gender.FEMALE),
                        new MatchingStatusQueryResult.ParticipantResult(null, 30, Gender.MALE)
                )
        );
    }

    private InstructorProfile approvedInstructorProfile(Long id, int level) {
        Member member = Member.create(
                "강사닉네임",
                "https://example.com/instructor.png",
                MemberRole.INSTRUCTOR,
                MemberStatus.ACTIVE
        );
        InstructorProfile instructorProfile = InstructorProfile.create(
                member,
                "김강사",
                "010-1234-5678",
                Gender.FEMALE,
                LocalDate.of(1998, 3, 1),
                "친절한 강사입니다.",
                LocalDate.of(2020, 1, 1),
                InstructorApprovalStatus.APPROVED,
                Instant.parse("2026-07-01T00:00:00Z")
        );
        ReflectionTestUtils.setField(instructorProfile, "id", id);
        ReflectionTestUtils.setField(instructorProfile, "level", level);
        ReflectionTestUtils.setField(
                instructorProfile,
                "certificateType",
                InstructorCertificateType.KSIA_SKI_LEVEL_3
        );
        return instructorProfile;
    }
}
