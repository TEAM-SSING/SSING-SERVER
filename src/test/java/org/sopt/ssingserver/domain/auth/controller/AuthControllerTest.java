package org.sopt.ssingserver.domain.auth.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.auth.dto.response.AuthRefreshResponse;
import org.sopt.ssingserver.domain.auth.service.AuthService;
import org.sopt.ssingserver.global.error.ErrorResponseFactory;
import org.sopt.ssingserver.global.logging.RequestIdFilter;
import org.sopt.ssingserver.global.security.AuthTokenExtractor;
import org.sopt.ssingserver.global.security.SecurityAccessDeniedHandler;
import org.sopt.ssingserver.global.security.SecurityAuthenticationEntryPoint;
import org.sopt.ssingserver.global.security.SecurityConfig;
import org.sopt.ssingserver.global.security.SecurityErrorResponseWriter;
import org.sopt.ssingserver.global.security.SecurityFilterSkipMatcher;
import org.sopt.ssingserver.global.security.access.AccessAuthorizationConfig;
import org.sopt.ssingserver.global.security.access.AccessAuthorizationService;
import org.sopt.ssingserver.global.security.access.CurrentMemberArgumentResolver;
import org.sopt.ssingserver.global.security.access.RequireAccessInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(controllers = AuthController.class)
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
class AuthControllerTest {

    private static final String REFRESH_TOKEN = "raw-refresh-token";
    private static final String REQUEST_ID = "req-auth-controller";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccessAuthorizationService accessAuthorizationService;

    @Test
    void refresh는_인증헤더_없이_새_Access_Token을_반환한다() throws Exception {
        when(authService.refreshAccessToken(REFRESH_TOKEN))
                .thenReturn(new AuthRefreshResponse("new-access-token", "Bearer", 3600));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"raw-refresh-token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_REISSUED"))
                .andExpect(jsonPath("$.message").value("Access Token이 재발급되었습니다."))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());

        verify(authService).refreshAccessToken(REFRESH_TOKEN);
        verifyNoInteractions(accessAuthorizationService);
    }

    @Test
    void refresh는_Refresh_Token이_공백이면_400을_반환하고_Service를_호출하지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.refreshToken").value("Refresh Token은 필수입니다."))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID));

        verifyNoInteractions(authService);
    }

    @Test
    void logout은_인증헤더_없이_204와_빈_body를_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"raw-refresh-token"}
                                """))
                .andExpect(status().isNoContent())
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID))
                .andExpect(content().string(""));

        verify(authService).logout(REFRESH_TOKEN);
        verifyNoInteractions(accessAuthorizationService);
    }

    @Test
    void logout은_Refresh_Token이_공백이면_400을_반환하고_Service를_호출하지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.refreshToken").value("Refresh Token은 필수입니다."))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID));

        verifyNoInteractions(authService);
    }
}
