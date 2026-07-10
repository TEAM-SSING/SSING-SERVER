package org.sopt.ssingserver.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;

@Schema(description = "강사 카카오 로그인 결과")
public record InstructorKakaoLoginResponse(
        @Schema(description = "SSING Access Token", example = "dummy_access_token")
        String accessToken,

        @Schema(description = "SSING Refresh Token", example = "dummy_refresh_token")
        String refreshToken,

        @Schema(description = "토큰 인증 타입", example = "Bearer")
        String tokenType,

        @Schema(description = "Access Token 만료까지 남은 초", example = "3600")
        long expiresIn,

        @Schema(description = "로그인한 강사 정보")
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

    @Schema(name = "InstructorAuthMemberResponse", description = "로그인한 강사 정보")
    public record MemberResponse(
            @Schema(description = "회원 ID", example = "1")
            Long id,

            @Schema(description = "회원 닉네임", example = "스키강사")
            String nickname,

            @Schema(description = "회원 역할", example = "INSTRUCTOR")
            MemberRole role,

            @Schema(description = "회원 상태", example = "ACTIVE")
            MemberStatus memberStatus,

            @Schema(description = "강사 승인 상태", example = "APPROVED")
            InstructorStatusResponse instructorStatus
    ) {
    }
}
