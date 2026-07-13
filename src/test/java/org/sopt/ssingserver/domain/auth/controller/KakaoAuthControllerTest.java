package org.sopt.ssingserver.domain.auth.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.auth.dto.response.AuthLoginResult;
import org.sopt.ssingserver.domain.auth.dto.response.InstructorAuthLoginResult;
import org.sopt.ssingserver.domain.auth.dto.response.InstructorStatusResponse;
import org.sopt.ssingserver.domain.auth.service.AuthService;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
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
@WebMvcTest(
        controllers = {
                ConsumerAuthController.class,
                InstructorAuthController.class
        }
)
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
class KakaoAuthControllerTest {

    private static final String KAKAO_ACCESS_TOKEN = "kakao-access-token";
    private static final String REQUEST_ID = "req-kakao-auth-controller";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccessAuthorizationService accessAuthorizationService;

    @Test
    void 소비자_카카오_로그인은_인증헤더_없이_200과_회원정보를_반환한다() throws Exception {
        AuthLoginResult loginResult = loginResult(MemberRole.CONSUMER, "소비자");
        when(authService.loginConsumerWithKakao(KAKAO_ACCESS_TOKEN)).thenReturn(loginResult);

        mockMvc.perform(post("/api/v1/consumer/auth/kakao")
                        .header(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kakaoAccessToken":"kakao-access-token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("AUTH_LOGIN_SUCCESS"))
                .andExpect(jsonPath("$.message").value("로그인에 성공했습니다."))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600))
                .andExpect(jsonPath("$.data.member.id").value(1))
                .andExpect(jsonPath("$.data.member.nickname").value("소비자"))
                .andExpect(jsonPath("$.data.member.role").value("CONSUMER"))
                .andExpect(jsonPath("$.data.member.memberStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.member.instructorStatus").doesNotExist());

        verify(authService).loginConsumerWithKakao(KAKAO_ACCESS_TOKEN);
        verifyNoInteractions(accessAuthorizationService);
    }

    @Test
    void 소비자_카카오_로그인은_Token이_공백이면_400이고_Service를_호출하지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/consumer/auth/kakao")
                        .header(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kakaoAccessToken":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.kakaoAccessToken").value("카카오 Access Token은 필수입니다."))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID));

        verifyNoInteractions(authService);
    }

    @Test
    void 강사_카카오_로그인은_인증헤더_없이_200과_강사승인상태를_반환한다() throws Exception {
        AuthLoginResult loginResult = loginResult(MemberRole.INSTRUCTOR, "강사");
        InstructorAuthLoginResult instructorResult = new InstructorAuthLoginResult(
                loginResult,
                InstructorStatusResponse.APPROVED
        );
        when(authService.loginInstructorWithKakao(KAKAO_ACCESS_TOKEN)).thenReturn(instructorResult);

        mockMvc.perform(post("/api/v1/instructor/auth/kakao")
                        .header(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kakaoAccessToken":"kakao-access-token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("AUTH_LOGIN_SUCCESS"))
                .andExpect(jsonPath("$.message").value("로그인에 성공했습니다."))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600))
                .andExpect(jsonPath("$.data.member.id").value(1))
                .andExpect(jsonPath("$.data.member.nickname").value("강사"))
                .andExpect(jsonPath("$.data.member.role").value("INSTRUCTOR"))
                .andExpect(jsonPath("$.data.member.memberStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.member.instructorStatus").value("APPROVED"));

        verify(authService).loginInstructorWithKakao(KAKAO_ACCESS_TOKEN);
        verifyNoInteractions(accessAuthorizationService);
    }

    @Test
    void 강사_카카오_로그인은_Token이_공백이면_400이고_Service를_호출하지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/instructor/auth/kakao")
                        .header(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kakaoAccessToken":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.kakaoAccessToken").value("카카오 Access Token은 필수입니다."))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID));

        verifyNoInteractions(authService);
    }

    private AuthLoginResult loginResult(MemberRole role, String nickname) {
        return new AuthLoginResult(
                "access-token",
                "refresh-token",
                "Bearer",
                3600,
                1L,
                nickname,
                role,
                MemberStatus.ACTIVE
        );
    }
}
