package org.sopt.ssingserver.global.security.access;

import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.global.security.AuthenticatedMember;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
@RequiredArgsConstructor
public class CurrentMemberArgumentResolver implements HandlerMethodArgumentResolver {

    private final AccessAuthorizationService accessAuthorizationService;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CurrentMember.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        Object currentMemberAttribute = webRequest.getAttribute(
                RequireAccessInterceptor.CURRENT_MEMBER_ATTRIBUTE,
                NativeWebRequest.SCOPE_REQUEST
        );
        if (currentMemberAttribute instanceof CurrentMember currentMember) {
            return currentMember;
        }

        AuthenticatedMember authenticatedMember = AuthenticatedMemberExtractor.getAuthenticatedMember();
        CurrentMember currentMember = accessAuthorizationService.authorize(authenticatedMember);
        webRequest.setAttribute(
                RequireAccessInterceptor.CURRENT_MEMBER_ATTRIBUTE,
                currentMember,
                NativeWebRequest.SCOPE_REQUEST
        );
        return currentMember;
    }
}
