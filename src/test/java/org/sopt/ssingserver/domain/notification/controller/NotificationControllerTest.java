package org.sopt.ssingserver.domain.notification.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.auth.token.AccessTokenClaims;
import org.sopt.ssingserver.domain.auth.token.AccessTokenProvider;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.notification.dto.response.NotificationListResponse;
import org.sopt.ssingserver.domain.notification.dto.response.NotificationListResponse.NotificationItemResponse;
import org.sopt.ssingserver.domain.notification.enums.NotificationType;
import org.sopt.ssingserver.domain.notification.service.NotificationService;
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
@WebMvcTest(controllers = NotificationController.class)
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
class NotificationControllerTest {

    private static final Long MEMBER_ID = 1L;
    private static final String ACCESS_TOKEN = "access-token";
    private static final String REQUEST_ID = "req-notification-controller";
    private static final Instant ISSUED_AT = Instant.parse("2026-07-13T00:00:00Z");
    private static final Instant EXPIRES_AT = ISSUED_AT.plusSeconds(3600);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private AccessTokenProvider accessTokenProvider;

    @MockitoBean
    private AccessAuthorizationService accessAuthorizationService;

    private AuthenticatedMember authenticatedMember;
    private CurrentMember currentMember;

    @BeforeEach
    void setUp() {
        authenticatedMember = new AuthenticatedMember(MEMBER_ID, MemberRole.INSTRUCTOR);
        currentMember = new CurrentMember(
                MEMBER_ID,
                MemberRole.INSTRUCTOR,
                MemberStatus.ACTIVE,
                null
        );
        when(accessTokenProvider.parseAccessToken(ACCESS_TOKEN)).thenReturn(new AccessTokenClaims(
                MEMBER_ID,
                MemberRole.INSTRUCTOR,
                ISSUED_AT,
                EXPIRES_AT
        ));
        when(accessAuthorizationService.authorize(
                authenticatedMember,
                AccessPolicy.CONSUMER,
                AccessPolicy.APPROVED_INSTRUCTOR
        ))
                .thenReturn(currentMember);
    }

    @Test
    void getNotifications는_공통응답으로_알림목록을_반환한다() throws Exception {
        NotificationListResponse response = new NotificationListResponse(
                List.of(new NotificationItemResponse(
                        99L,
                        NotificationType.MATCHING_OFFER_RECEIVED,
                        "씽 매칭 강습 도착",
                        "새로운 강습이 도착했어요. 강습생 정보를 확인하고 강습을 수락해보세요.",
                        false,
                        Instant.parse("2026-07-14T10:00:00Z")
                )),
                "next-cursor",
                true
        );
        when(notificationService.getNotifications(currentMember, null, 20))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .header(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("요청이 성공했습니다."))
                .andExpect(jsonPath("$.data.notifications[0].notificationId").value(99))
                .andExpect(jsonPath("$.data.notifications[0].type").value("MATCHING_OFFER_RECEIVED"))
                .andExpect(jsonPath("$.data.notifications[0].title").value("씽 매칭 강습 도착"))
                .andExpect(jsonPath("$.data.notifications[0].body").value(
                        "새로운 강습이 도착했어요. 강습생 정보를 확인하고 강습을 수락해보세요."
                ))
                .andExpect(jsonPath("$.data.notifications[0].isRead").value(false))
                .andExpect(jsonPath("$.data.notifications[0].createdAt").value("2026-07-14T10:00:00Z"))
                .andExpect(jsonPath("$.data.nextCursor").value("next-cursor"))
                .andExpect(jsonPath("$.data.hasNext").value(true));

        verify(notificationService).getNotifications(currentMember, null, 20);
    }

    @Test
    void getNotifications는_size가_범위를_벗어나면_400이고_Service를_호출하지_않는다() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                        .param("size", "0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .header(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.size").value("조회할 알림 개수는 1개 이상이어야 합니다."))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID));

        verifyNoInteractions(notificationService);
    }

    @Test
    void getNotifications는_size가_100을_초과하면_명시적인_검증메시지로_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                        .param("size", "101")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .header(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.size").value("조회할 알림 개수는 100개 이하여야 합니다."))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID));

        verifyNoInteractions(notificationService);
    }

    @Test
    void getNotifications는_인증헤더가_없으면_401이고_Service를_호출하지_않는다() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                        .header(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID));

        verifyNoInteractions(notificationService);
    }
}
