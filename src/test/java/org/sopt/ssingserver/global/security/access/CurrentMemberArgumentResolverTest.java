package org.sopt.ssingserver.global.security.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.ServletWebRequest;

@ExtendWith(MockitoExtension.class)
class CurrentMemberArgumentResolverTest {

    @Mock
    private AccessAuthorizationService accessAuthorizationService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void supportsParameter는_CurrentMember_파라미터를_지원한다() throws Exception {
        CurrentMemberArgumentResolver resolver = createResolver();

        assertThat(resolver.supportsParameter(methodParameter("currentMember"))).isTrue();
        assertThat(resolver.supportsParameter(methodParameter("stringParameter"))).isFalse();
    }

    @Test
    void resolveArgument는_request에_저장된_CurrentMember를_우선_사용한다() throws Exception {
        CurrentMemberArgumentResolver resolver = createResolver();
        CurrentMember currentMember = currentMember();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequireAccessInterceptor.CURRENT_MEMBER_ATTRIBUTE, currentMember);

        Object result = resolver.resolveArgument(
                methodParameter("currentMember"),
                null,
                new ServletWebRequest(request),
                null
        );

        assertThat(result).isSameAs(currentMember);
        verifyNoInteractions(accessAuthorizationService);
    }

    @Test
    void resolveArgument는_request에_CurrentMember가_없으면_기본_인가를_수행하고_저장한다() throws Exception {
        CurrentMemberArgumentResolver resolver = createResolver();
        CurrentMember currentMember = currentMember();
        AuthenticatedMember principal = new AuthenticatedMember(1L, MemberRole.CONSUMER);
        MockHttpServletRequest request = new MockHttpServletRequest();
        setAuthentication(principal);

        when(accessAuthorizationService.authorize(principal)).thenReturn(currentMember);

        Object result = resolver.resolveArgument(
                methodParameter("currentMember"),
                null,
                new ServletWebRequest(request),
                null
        );

        assertThat(result).isSameAs(currentMember);
        assertThat(request.getAttribute(RequireAccessInterceptor.CURRENT_MEMBER_ATTRIBUTE)).isSameAs(currentMember);
        verify(accessAuthorizationService).authorize(principal);
    }

    @Test
    void resolveArgument는_인증_주체가_없으면_UNAUTHENTICATED를_던진다() throws Exception {
        CurrentMemberArgumentResolver resolver = createResolver();

        assertThatThrownBy(() -> resolver.resolveArgument(
                methodParameter("currentMember"),
                null,
                new ServletWebRequest(new MockHttpServletRequest()),
                null
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isSameAs(CommonErrorCode.UNAUTHENTICATED));
    }

    private CurrentMemberArgumentResolver createResolver() {
        return new CurrentMemberArgumentResolver(accessAuthorizationService);
    }

    private MethodParameter methodParameter(String methodName) throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod(methodName, methodParameterType(methodName));
        return new MethodParameter(method, 0);
    }

    private Class<?> methodParameterType(String methodName) {
        if ("stringParameter".equals(methodName)) {
            return String.class;
        }
        return CurrentMember.class;
    }

    private void setAuthentication(AuthenticatedMember principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null)
        );
    }

    private CurrentMember currentMember() {
        return new CurrentMember(1L, MemberRole.CONSUMER, MemberStatus.ACTIVE, null);
    }

    private static class TestController {

        void currentMember(CurrentMember currentMember) {
        }

        void stringParameter(String value) {
        }
    }
}
