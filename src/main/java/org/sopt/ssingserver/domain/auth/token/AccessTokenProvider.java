package org.sopt.ssingserver.domain.auth.token;

import org.sopt.ssingserver.domain.member.enums.MemberRole;

public interface AccessTokenProvider {

    String createAccessToken(Long memberId, MemberRole role);

    AccessTokenClaims parseAccessToken(String token);
}
