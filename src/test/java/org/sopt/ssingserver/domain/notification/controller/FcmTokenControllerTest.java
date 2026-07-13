package org.sopt.ssingserver.domain.notification.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.auth.token.AccessTokenClaims;
import org.sopt.ssingserver.domain.auth.token.AccessTokenProvider;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.notification.dto.request.DeleteFcmTokenRequest;
import org.sopt.ssingserver.domain.notification.dto.request.RegisterFcmTokenRequest;
import org.sopt.ssingserver.domain.notification.enums.ClientApp;
import org.sopt.ssingserver.domain.notification.enums.ClientPlatform;
import org.sopt.ssingserver.domain.notification.service.FcmTokenService;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(controllers = FcmTokenController.class)
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
class FcmTokenControllerTest {

    private static final Long MEMBER_ID = 1L;
    private static final String ACCESS_TOKEN = "access-token";
    private static final String FCM_TOKEN = "fcm-token";
    private static final String REQUEST_ID = "req-fcm-token-controller";
    private static final Instant ISSUED_AT = Instant.parse("2026-07-13T00:00:00Z");
    private static final Instant EXPIRES_AT = ISSUED_AT.plusSeconds(3600);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FcmTokenService fcmTokenService;

    @MockitoBean
    private AccessTokenProvider accessTokenProvider;

    @MockitoBean
    private AccessAuthorizationService accessAuthorizationService;

    private AuthenticatedMember authenticatedMember;
    private CurrentMember currentMember;

    @BeforeEach
    void setUp() {
        authenticatedMember = new AuthenticatedMember(MEMBER_ID, MemberRole.CONSUMER);
        currentMember = new CurrentMember(
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
        when(accessAuthorizationService.authorize(authenticatedMember, AccessPolicy.ACTIVE_MEMBER))
                .thenReturn(currentMember);
    }

    @Test
    void registerOrUpdate는_인증된_회원이면_204와_빈_body를_반환한다() throws Exception {
        mockMvc.perform(put("/api/v1/fcm-tokens")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .header(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientApp":"CONSUMER",
                                  "platform":"ANDROID",
                                  "fcmToken":"fcm-token"
                                }
                                """))
                .andExpect(status().isNoContent())
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID))
                .andExpect(content().string(""));

        verify(accessAuthorizationService).authorize(authenticatedMember, AccessPolicy.ACTIVE_MEMBER);
        verify(fcmTokenService).registerOrUpdate(
                MEMBER_ID,
                new RegisterFcmTokenRequest(ClientApp.CONSUMER, ClientPlatform.ANDROID, FCM_TOKEN)
        );
    }

    @Test
    void registerOrUpdate는_Token이_공백이면_400이고_Service를_호출하지_않는다() throws Exception {
        mockMvc.perform(put("/api/v1/fcm-tokens")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .header(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientApp":"CONSUMER",
                                  "platform":"ANDROID",
                                  "fcmToken":" "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.fcmToken").value("FCM token은 필수입니다."))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID));

        verifyNoInteractions(fcmTokenService);
    }

    @Test
    void unregister는_인증된_회원이면_204와_빈_body를_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/fcm-tokens/unregister")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .header(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fcmToken":"fcm-token"}
                                """))
                .andExpect(status().isNoContent())
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID))
                .andExpect(content().string(""));

        verify(accessAuthorizationService).authorize(authenticatedMember, AccessPolicy.ACTIVE_MEMBER);
        verify(fcmTokenService).unregister(MEMBER_ID, new DeleteFcmTokenRequest(FCM_TOKEN));
    }

    @Test
    void unregister는_Token이_공백이면_400이고_Service를_호출하지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/fcm-tokens/unregister")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .header(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fcmToken":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.fcmToken").value("FCM token은 필수입니다."))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID));

        verifyNoInteractions(fcmTokenService);
    }

    @Test
    void registerOrUpdate는_인증헤더가_없으면_401이고_Service를_호출하지_않는다() throws Exception {
        mockMvc.perform(put("/api/v1/fcm-tokens")
                        .header(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientApp":"CONSUMER",
                                  "platform":"ANDROID",
                                  "fcmToken":"fcm-token"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID));

        verifyNoInteractions(fcmTokenService);
    }
}
