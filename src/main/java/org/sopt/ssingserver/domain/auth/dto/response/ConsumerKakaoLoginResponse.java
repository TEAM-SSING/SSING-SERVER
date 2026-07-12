package org.sopt.ssingserver.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;

@Schema(description = "소비자 카카오 로그인 결과")
public record ConsumerKakaoLoginResponse(
        @Schema(description = "SSING Access Token", example = "dummy_access_token")
        String accessToken,

        @Schema(description = "SSING Refresh Token", example = "dummy_refresh_token")
        String refreshToken,

        @Schema(description = "토큰 인증 타입", example = "Bearer")
        String tokenType,

        @Schema(description = "Access Token 만료까지 남은 초", example = "3600")
        long expiresIn,

        @Schema(description = "로그인한 소비자 정보")
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

    @Schema(name = "ConsumerAuthMemberResponse", description = "로그인한 소비자 정보")
    public record MemberResponse(
            @Schema(description = "회원 ID", example = "1")
            Long id,

            @Schema(description = "회원 닉네임", example = "스키러버")
            String nickname,

            @Schema(description = "회원 역할", example = "CONSUMER")
            MemberRole role,

            @Schema(description = "회원 상태", example = "ACTIVE")
            MemberStatus memberStatus
    ) {
    }
}
