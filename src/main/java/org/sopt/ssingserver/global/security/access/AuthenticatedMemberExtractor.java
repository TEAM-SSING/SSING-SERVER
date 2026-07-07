package org.sopt.ssingserver.global.security.access;

import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.security.AuthenticatedMember;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

final class AuthenticatedMemberExtractor {

    private AuthenticatedMemberExtractor() {
    }

    static AuthenticatedMember getAuthenticatedMember() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedMember authenticatedMember)) {
            throw new BusinessException(CommonErrorCode.UNAUTHENTICATED);
        }
        return authenticatedMember;
    }
}
