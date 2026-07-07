package org.sopt.ssingserver.global.security.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.security.AuthenticatedMember;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

@ExtendWith(MockitoExtension.class)
class RequireAccessInterceptorTest {

    @Mock
    private AccessAuthorizationService accessAuthorizationService;

    @Mock
    private HttpServletResponse response;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void preHandle은_RequireAccess가_없으면_인가_검사를_하지_않는다() throws Exception {
        RequireAccessInterceptor interceptor = createInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest();
        HandlerMethod handlerMethod = handlerMethod(new TestController(), "withoutRequireAccess");

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertThat(result).isTrue();
        verifyNoInteractions(accessAuthorizationService);
    }

    @Test
    void preHandle은_method_RequireAccess를_읽고_CurrentMember를_request에_저장한다() throws Exception {
        RequireAccessInterceptor interceptor = createInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest();
        HandlerMethod handlerMethod = handlerMethod(new TestController(), "consumerOnly");
        AuthenticatedMember principal = authenticatedMember(MemberRole.CONSUMER);
        CurrentMember currentMember = currentMember(MemberRole.CONSUMER);
        setAuthentication(principal);

        when(accessAuthorizationService.authorize(principal, AccessPolicy.CONSUMER)).thenReturn(currentMember);

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertThat(result).isTrue();
        assertThat(request.getAttribute(RequireAccessInterceptor.CURRENT_MEMBER_ATTRIBUTE)).isSameAs(currentMember);
    }

    @Test
    void preHandle은_class_RequireAccess를_읽는다() throws Exception {
        RequireAccessInterceptor interceptor = createInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest();
        HandlerMethod handlerMethod = handlerMethod(new AdminController(), "classLevel");
        AuthenticatedMember principal = authenticatedMember(MemberRole.ADMIN);
        CurrentMember currentMember = currentMember(MemberRole.ADMIN);
        setAuthentication(principal);

        when(accessAuthorizationService.authorize(principal, AccessPolicy.ADMIN)).thenReturn(currentMember);

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertThat(result).isTrue();
        assertThat(request.getAttribute(RequireAccessInterceptor.CURRENT_MEMBER_ATTRIBUTE)).isSameAs(currentMember);
    }

    @Test
    void preHandle은_method_RequireAccess가_class_RequireAccess를_덮어쓴다() throws Exception {
        RequireAccessInterceptor interceptor = createInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest();
        HandlerMethod handlerMethod = handlerMethod(new AdminController(), "methodOverride");
        AuthenticatedMember principal = authenticatedMember(MemberRole.CONSUMER);
        CurrentMember currentMember = currentMember(MemberRole.CONSUMER);
        setAuthentication(principal);

        when(accessAuthorizationService.authorize(principal, AccessPolicy.CONSUMER)).thenReturn(currentMember);

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertThat(result).isTrue();
        assertThat(request.getAttribute(RequireAccessInterceptor.CURRENT_MEMBER_ATTRIBUTE)).isSameAs(currentMember);
    }

    @Test
    void preHandle은_인증_주체가_없으면_UNAUTHENTICATED를_던진다() throws Exception {
        RequireAccessInterceptor interceptor = createInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest();
        HandlerMethod handlerMethod = handlerMethod(new TestController(), "activeMember");

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isSameAs(CommonErrorCode.UNAUTHENTICATED));
    }

    private RequireAccessInterceptor createInterceptor() {
        return new RequireAccessInterceptor(accessAuthorizationService);
    }

    private HandlerMethod handlerMethod(Object controller, String methodName) throws NoSuchMethodException {
        Method method = controller.getClass().getDeclaredMethod(methodName);
        return new HandlerMethod(controller, method);
    }

    private void setAuthentication(AuthenticatedMember principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null)
        );
    }

    private AuthenticatedMember authenticatedMember(MemberRole role) {
        return new AuthenticatedMember(1L, role);
    }

    private CurrentMember currentMember(MemberRole role) {
        return new CurrentMember(1L, role, MemberStatus.ACTIVE, null);
    }

    private static class TestController {

        void withoutRequireAccess() {
        }

        @RequireAccess
        void activeMember() {
        }

        @RequireAccess(AccessPolicy.CONSUMER)
        void consumerOnly() {
        }
    }

    @RequireAccess(AccessPolicy.ADMIN)
    private static class AdminController {

        void classLevel() {
        }

        @RequireAccess(AccessPolicy.CONSUMER)
        void methodOverride() {
        }
    }
}
