package org.sopt.ssingserver.domain.auth.dto.response;

import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;

public record InstructorKakaoLoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        MemberResponse member
) {

    public record MemberResponse(
            Long id,
            String nickname,
            MemberRole role,
            MemberStatus memberStatus,
            String instructorStatus
    ) {
    }
}
