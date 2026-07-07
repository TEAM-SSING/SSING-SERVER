package org.sopt.ssingserver.global.security.access;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.global.security.AuthenticatedMember;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class RequireAccessInterceptor implements HandlerInterceptor {

    public static final String CURRENT_MEMBER_ATTRIBUTE = RequireAccessInterceptor.class.getName() + ".CURRENT_MEMBER";

    private final AccessAuthorizationService accessAuthorizationService;

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequireAccess requireAccess = findRequireAccess(handlerMethod);
        if (requireAccess == null) {
            return true;
        }

        AuthenticatedMember authenticatedMember = AuthenticatedMemberExtractor.getAuthenticatedMember();
        CurrentMember currentMember = accessAuthorizationService.authorize(authenticatedMember, requireAccess.value());

        // 인가 단계에서 조회한 DB 현재값을 컨트롤러 파라미터 주입 시 재사용한다.
        request.setAttribute(CURRENT_MEMBER_ATTRIBUTE, currentMember);
        return true;
    }

    private RequireAccess findRequireAccess(HandlerMethod handlerMethod) {
        RequireAccess methodAnnotation = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(),
                RequireAccess.class
        );
        if (methodAnnotation != null) {
            return methodAnnotation;
        }
        return AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RequireAccess.class);
    }
}
