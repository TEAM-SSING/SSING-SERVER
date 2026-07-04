package org.sopt.ssingserver.domain.auth.dto.response;

import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;

public record ConsumerKakaoLoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        MemberResponse member
) {

    public static ConsumerKakaoLoginResponse from(AuthLoginResult result) {
        return new ConsumerKakaoLoginResponse(
                result.accessToken(),
                result.refreshToken(),
                result.tokenType(),
                result.expiresIn(),
                new MemberResponse(
                        result.memberId(),
                        result.nickname(),
                        result.role(),
                        result.memberStatus()
                )
        );
    }

    public record MemberResponse(
            Long id,
            String nickname,
            MemberRole role,
            MemberStatus memberStatus
    ) {
    }
}
