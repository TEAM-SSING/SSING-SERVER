package org.sopt.ssingserver.domain.matching.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.instructor.enums.InstructorCertificateType;
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
