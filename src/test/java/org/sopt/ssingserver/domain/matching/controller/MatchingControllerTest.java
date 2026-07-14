package org.sopt.ssingserver.domain.matching.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.auth.token.AccessTokenClaims;
import org.sopt.ssingserver.domain.auth.token.AccessTokenProvider;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingStatusQueryResult;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
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
    void getActiveStatus는_활성_요청이_있으면_기존_상태응답을_200으로_반환한다() throws Exception {
        when(matchingStatusQueryService.getActiveStatus(MEMBER_ID))
                .thenReturn(Optional.of(searchingResult()));

        mockMvc.perform(get("/api/v1/consumer/matching-requests/active")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.matchingRequestId").value(10L))
                .andExpect(jsonPath("$.data.matchingStatus").value("SEARCHING"))
                .andExpect(jsonPath("$.data.requestStatus").value("REQUESTED"));

        verify(matchingStatusQueryService).getActiveStatus(MEMBER_ID);
    }

    @Test
    void getActiveStatus는_활성_요청이_없으면_body없이_204를_반환한다() throws Exception {
        when(matchingStatusQueryService.getActiveStatus(MEMBER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/consumer/matching-requests/active")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(matchingStatusQueryService).getActiveStatus(MEMBER_ID);
    }

    @Test
    void getActiveStatus는_인증이_없으면_401이고_Service를_호출하지_않는다() throws Exception {
        mockMvc.perform(get("/api/v1/consumer/matching-requests/active"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(matchingStatusQueryService);
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
                null
        );
    }
}
