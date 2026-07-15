package org.sopt.ssingserver.domain.matching.controller;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.auth.token.AccessTokenClaims;
import org.sopt.ssingserver.domain.auth.token.AccessTokenProvider;
import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.matching.dto.result.InstructorMatchingOfferDetailResult;
import org.sopt.ssingserver.domain.matching.dto.result.InstructorMatchingOffersResult;
import org.sopt.ssingserver.domain.matching.error.MatchingErrorCode;
import org.sopt.ssingserver.domain.matching.service.InstructorMatchingOfferService;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.global.error.BusinessException;
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
@WebMvcTest(controllers = InstructorMatchingOfferController.class)
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
class InstructorMatchingOfferControllerTest {

    private static final Long MEMBER_ID = 1L;
    private static final String ACCESS_TOKEN = "access-token";
    private static final Instant ISSUED_AT = Instant.parse("2026-07-14T00:00:00Z");
    private static final Instant EXPIRES_AT = ISSUED_AT.plusSeconds(3600);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InstructorMatchingOfferService instructorMatchingOfferService;

    @MockitoBean
    private AccessTokenProvider accessTokenProvider;

    @MockitoBean
    private AccessAuthorizationService accessAuthorizationService;

    @BeforeEach
    void setUp() {
        AuthenticatedMember authenticatedMember = new AuthenticatedMember(MEMBER_ID, MemberRole.INSTRUCTOR);
        CurrentMember currentMember = new CurrentMember(
                MEMBER_ID,
                MemberRole.INSTRUCTOR,
                MemberStatus.ACTIVE,
                InstructorApprovalStatus.APPROVED
        );
        when(accessTokenProvider.parseAccessToken(ACCESS_TOKEN)).thenReturn(new AccessTokenClaims(
                MEMBER_ID,
                MemberRole.INSTRUCTOR,
                ISSUED_AT,
                EXPIRES_AT
        ));
        when(accessAuthorizationService.authorize(authenticatedMember, AccessPolicy.APPROVED_INSTRUCTOR))
                .thenReturn(currentMember);
    }

    @Test
    void getCurrentOffers는_제안이_없으면_offerId_null과_대기조건을_반환한다() throws Exception {
        InstructorMatchingOffersResult result = new InstructorMatchingOffersResult(
                null,
                new InstructorMatchingOffersResult.MatchingSettingResult(
                        true,
                        new InstructorMatchingOffersResult.ResortResult("HIGH1", "하이원"),
                        Sport.SNOWBOARD,
                        List.of(LessonLevel.FIRST_TIME, LessonLevel.INTERMEDIATE),
                        List.of(120, 240),
                        3,
                        true
                )
        );
        when(instructorMatchingOfferService.getCurrentOffers(MEMBER_ID)).thenReturn(result);

        mockMvc.perform(get("/api/v1/instructor/matching-offers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(hasKey("offerId")))
                .andExpect(jsonPath("$.data.offerId").value(nullValue()))
                .andExpect(jsonPath("$.data.matchingSetting.isExposed").value(true))
                .andExpect(jsonPath("$.data.matchingSetting.resort.code").value("HIGH1"))
                .andExpect(jsonPath("$.data.matchingSetting.resort.displayName").value("하이원"))
                .andExpect(jsonPath("$.data.matchingSetting.sport").value("SNOWBOARD"))
                .andExpect(jsonPath("$.data.matchingSetting.lessonLevels[0]").value("FIRST_TIME"))
                .andExpect(jsonPath("$.data.matchingSetting.availableDurationMinutes[0]").value(120))
                .andExpect(jsonPath("$.data.matchingSetting.maxHeadcount").value(3))
                .andExpect(jsonPath("$.data.matchingSetting.equipmentReady").value(true))
                .andExpect(jsonPath("$.data.items").doesNotExist())
                .andExpect(jsonPath("$.data.currentPage").doesNotExist())
                .andExpect(jsonPath("$.data.size").doesNotExist())
                .andExpect(jsonPath("$.data.hasNext").doesNotExist())
                .andExpect(jsonPath("$.data.activeOffer").doesNotExist());

        verify(instructorMatchingOfferService).getCurrentOffers(MEMBER_ID);
    }

    @Test
    void getOfferDetail은_본인_종료_제안을_MATCHING_NOT_ACTIVE_409로_반환한다() throws Exception {
        when(instructorMatchingOfferService.getOfferDetail(MEMBER_ID, 21L))
                .thenThrow(new BusinessException(MatchingErrorCode.MATCHING_NOT_ACTIVE));

        mockMvc.perform(get("/api/v1/instructor/matching-offers/{offerId}", 21L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("MATCHING_NOT_ACTIVE"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(instructorMatchingOfferService).getOfferDetail(MEMBER_ID, 21L);
    }

    @Test
    void getOfferDetail은_인증이_없으면_401이고_Service를_호출하지_않는다() throws Exception {
        mockMvc.perform(get("/api/v1/instructor/matching-offers/{offerId}", 21L))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(instructorMatchingOfferService);
    }
}
