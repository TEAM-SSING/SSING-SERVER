package org.sopt.ssingserver.domain.matching.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.auth.token.AccessTokenClaims;
import org.sopt.ssingserver.domain.auth.token.AccessTokenProvider;
import org.sopt.ssingserver.domain.instructor.enums.InstructorCertificateType;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingPriceSummaryResult;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingProgressSummaryResult;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingStatusQueryResult;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupItemStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.member.enums.Gender;
import org.sopt.ssingserver.domain.matching.service.MatchingStatusQueryService;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.global.error.ErrorResponseFactory;
import org.sopt.ssingserver.global.logging.RequestIdFilter;
import org.sopt.ssingserver.global.security.AuthTokenExtractor;
import org.sopt.ssingserver.global.security.AuthenticatedMember;
import org.sopt.ssingserver.global.security.SecurityAccessDeniedHandler;
import org.sopt.ssingserver.global.security.SecurityAuthenticationEntryPoint;
import org.sopt.ssingserver.global.security.SecurityConfig;
import org.sopt.ssingserver.global.security.SecurityErrorResponseWriter;
import org.sopt.ssingserver.global.security.SecurityFilterSkipMatcher;
import org.sopt.ssingserver.global.security.access.AccessAuthorizationConfig;
import org.sopt.ssingserver.global.security.access.AccessAuthorizationService;
import org.sopt.ssingserver.global.security.access.AccessPolicy;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.sopt.ssingserver.global.security.access.CurrentMemberArgumentResolver;
import org.sopt.ssingserver.global.security.access.RequireAccessInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(controllers = MatchingController.class)
@ImportAutoConfiguration({
        ServletWebSecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class
})
@Import({
        SecurityConfig.class,
        AuthTokenExtractor.class,
        SecurityFilterSkipMatcher.class,
        SecurityAuthenticationEntryPoint.class,
        SecurityAccessDeniedHandler.class,
        SecurityErrorResponseWriter.class,
        ErrorResponseFactory.class,
        RequestIdFilter.class,
        AccessAuthorizationConfig.class,
        CurrentMemberArgumentResolver.class,
        RequireAccessInterceptor.class
})
class MatchingControllerTest {

    private static final Long MEMBER_ID = 1L;
    private static final String ACCESS_TOKEN = "access-token";
    private static final Instant ISSUED_AT = Instant.parse("2026-07-14T00:00:00Z");
    private static final Instant EXPIRES_AT = ISSUED_AT.plusSeconds(3600);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MatchingStatusQueryService matchingStatusQueryService;

    @MockitoBean
    private AccessTokenProvider accessTokenProvider;

    @MockitoBean
    private AccessAuthorizationService accessAuthorizationService;

    @BeforeEach
    void setUp() {
        AuthenticatedMember authenticatedMember = new AuthenticatedMember(MEMBER_ID, MemberRole.CONSUMER);
        CurrentMember currentMember = new CurrentMember(
                MEMBER_ID,
                MemberRole.CONSUMER,
                MemberStatus.ACTIVE,
                null
        );
        when(accessTokenProvider.parseAccessToken(ACCESS_TOKEN)).thenReturn(new AccessTokenClaims(
                MEMBER_ID,
                MemberRole.CONSUMER,
                ISSUED_AT,
                EXPIRES_AT
        ));
        when(accessAuthorizationService.authorize(authenticatedMember, AccessPolicy.CONSUMER))
                .thenReturn(currentMember);
    }

    @Test
    void getActiveStatus는_SEARCHING이면_요청요약만_담아_200으로_반환한다() throws Exception {
        when(matchingStatusQueryService.getActiveStatus(MEMBER_ID))
                .thenReturn(Optional.of(searchingResult()));

        mockMvc.perform(get("/api/v1/consumer/matching-requests/active")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.recoveryState").value("ACTIVE"))
                .andExpect(jsonPath("$.data.matchingRequestId").value(10L))
                .andExpect(jsonPath("$.data.matchingStatus").value("SEARCHING"))
                .andExpect(jsonPath("$.data.requestStatus").value("REQUESTED"))
                .andExpect(jsonPath("$.data.requestSummary.resort.code").value("HIGH1"))
                .andExpect(jsonPath("$.data.requestSummary.resort.displayName").value("하이원"))
                .andExpect(jsonPath("$.data.requestSummary.sport").value("SNOWBOARD"))
                .andExpect(jsonPath("$.data.requestSummary.lessonLevel").value("FIRST_TIME"))
                .andExpect(jsonPath("$.data.requestSummary.headcount").value(2))
                .andExpect(jsonPath("$.data.requestSummary.requesterName").value("요청자"))
                .andExpect(jsonPath("$.data.requestSummary.participants[0].name").value("홍길동"))
                .andExpect(jsonPath("$.data.requestSummary.participants[0].age").value(24))
                .andExpect(jsonPath("$.data.requestSummary.participants[0].gender").value("FEMALE"))
                .andExpect(jsonPath("$.data.requestSummary.participants[1].name").doesNotExist())
                .andExpect(jsonPath("$.data.requestSummary.participants[1].age").value(30))
                .andExpect(jsonPath("$.data.groupId").doesNotExist())
                .andExpect(jsonPath("$.data.offerStatus").doesNotExist())
                .andExpect(jsonPath("$.data.paymentStatus").doesNotExist())
                .andExpect(jsonPath("$.data.lessonSummary").doesNotExist())
                .andExpect(jsonPath("$.data.instructorProfile").doesNotExist())
                .andExpect(jsonPath("$.data.progressSummary").doesNotExist())
                .andExpect(jsonPath("$.data.priceSummary").doesNotExist())
                .andExpect(jsonPath("$.data.payload").doesNotExist())
                .andExpect(jsonPath("$.data.expiresAt").doesNotExist())
                .andExpect(jsonPath("$.data.lessonId").doesNotExist());

        verify(matchingStatusQueryService).getActiveStatus(MEMBER_ID);
    }

    @Test
    void getActiveStatus는_최종확인이면_강습과_강사_화면블록을_200으로_반환한다() throws Exception {
        when(matchingStatusQueryService.getActiveStatus(MEMBER_ID))
                .thenReturn(Optional.of(confirmationResult()));

        mockMvc.perform(get("/api/v1/consumer/matching-requests/active")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recoveryState").value("ACTIVE"))
                .andExpect(jsonPath("$.data.matchingStatus").value("WAITING_FOR_CONFIRMATION"))
                .andExpect(jsonPath("$.data.lessonSummary.durationMinutes").value(120))
                .andExpect(jsonPath("$.data.lessonSummary.totalHeadcount").value(4))
                .andExpect(jsonPath("$.data.lessonSummary.startType").value("IMMEDIATE"))
                .andExpect(jsonPath("$.data.instructorProfile.instructorId").value(40L))
                .andExpect(jsonPath("$.data.instructorProfile.careerYears").value(6))
                .andExpect(jsonPath("$.data.instructorProfile.completedLessonCount").value(24L))
                .andExpect(jsonPath("$.data.instructorProfile.averageRating").value(4.7))
                .andExpect(jsonPath("$.data.instructorProfile.certificateTypes[0]")
                        .value("KSIA_SNOWBOARD_LEVEL_2"))
                .andExpect(jsonPath("$.data.instructorProfile.latestReview.content")
                        .value("설명을 친절하게 해주셨어요."))
                .andExpect(jsonPath("$.data.paymentStatus").doesNotExist())
                .andExpect(jsonPath("$.data.expiresAt").doesNotExist())
                .andExpect(jsonPath("$.data.lessonId").doesNotExist());

        verify(matchingStatusQueryService).getActiveStatus(MEMBER_ID);
    }

    @Test
    void getActiveStatus는_활성_요청이_없으면_NONE만_담아_200으로_반환한다() throws Exception {
        when(matchingStatusQueryService.getActiveStatus(MEMBER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/consumer/matching-requests/active")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("요청이 성공했습니다."))
                .andExpect(jsonPath("$.data.recoveryState").value("NONE"))
                .andExpect(jsonPath("$.data.matchingRequestId").doesNotExist())
                .andExpect(jsonPath("$.data.matchingStatus").doesNotExist())
                .andExpect(jsonPath("$.data.payload").doesNotExist());

        verify(matchingStatusQueryService).getActiveStatus(MEMBER_ID);
    }

    @Test
    void getActiveStatus는_인증이_없으면_401이고_Service를_호출하지_않는다() throws Exception {
        mockMvc.perform(get("/api/v1/consumer/matching-requests/active"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(matchingStatusQueryService);
    }

    @Test
    void getStatus는_ID조회_기존응답에_recoveryState를_추가하지_않는다() throws Exception {
        when(matchingStatusQueryService.getStatus(MEMBER_ID, 10L)).thenReturn(searchingResult());

        mockMvc.perform(get("/api/v1/consumer/matching-requests/{matchingRequestId}", 10L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchingRequestId").value(10L))
                .andExpect(jsonPath("$.data.matchingStatus").value("SEARCHING"))
                .andExpect(jsonPath("$.data.recoveryState").doesNotExist())
                .andExpect(jsonPath("$.data.requestSummary").doesNotExist());

        verify(matchingStatusQueryService).getStatus(MEMBER_ID, 10L);
    }

    private MatchingStatusQueryResult searchingResult() {
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
                requestSummary(),
                null
        );
    }

    private MatchingStatusQueryResult confirmationResult() {
        return new MatchingStatusQueryResult(
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
                null,
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
                        new MatchingStatusQueryResult.LatestReviewResult("설명을 친절하게 해주셨어요.")
                ),
                null,
                new MatchingPriceSummaryResult(80_000, 20_000, 100_000),
                requestSummary(),
                new MatchingStatusQueryResult.LessonSummaryResult(120, 4, "IMMEDIATE")
        );
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
}
