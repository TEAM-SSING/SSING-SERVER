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

    public static InstructorKakaoLoginResponse from(InstructorAuthLoginResult result) {
        AuthLoginResult loginResult = result.loginResult();
        return new InstructorKakaoLoginResponse(
                loginResult.accessToken(),
                loginResult.refreshToken(),
                loginResult.tokenType(),
                loginResult.expiresIn(),
                new MemberResponse(
                        loginResult.memberId(),
                        loginResult.nickname(),
                        loginResult.role(),
                        loginResult.memberStatus(),
                        result.instructorStatus()
                )
        );
    }

    public record MemberResponse(
            Long id,
            String nickname,
            MemberRole role,
            MemberStatus memberStatus,
            InstructorStatusResponse instructorStatus
    ) {
    }
}
