package org.sopt.ssingserver.domain.auth.dto.response;

import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;

public record AuthLoginResult(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        Long memberId,
        String nickname,
        MemberRole role,
        MemberStatus memberStatus
) {
}
