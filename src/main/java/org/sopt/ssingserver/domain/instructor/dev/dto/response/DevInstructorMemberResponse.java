package org.sopt.ssingserver.domain.instructor.dev.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.sopt.ssingserver.domain.auth.enums.OAuthProvider;
import org.sopt.ssingserver.domain.instructor.dev.enums.DevInstructorActionKey;
import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.instructor.enums.InstructorCertificateType;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;

@Schema(description = "실제 카카오 OAuth 회원의 강사 관련 현재 상태")
public record DevInstructorMemberResponse(
        @Schema(description = "OAuth 계정 raw ID")
        Long oauthAccountId,
        @Schema(description = "OAuth 제공자", example = "KAKAO")
        OAuthProvider provider,
        @Schema(description = "회원 raw ID")
        Long memberId,
        @Schema(description = "카카오 로그인으로 저장된 닉네임")
        String nickname,
        @Schema(description = "현재 회원 역할")
        MemberRole memberRole,
        @Schema(description = "현재 회원 상태")
        MemberStatus memberStatus,
        @Schema(description = "회원 생성 시각")
        Instant memberCreatedAt,
        @Schema(description = "강사 프로필 raw ID. 없으면 값 없음")
        Long instructorProfileId,
        @Schema(description = "강사 승인 상태. 프로필이 없으면 값 없음")
        InstructorApprovalStatus instructorApprovalStatus,
        @Schema(description = "승인 시각. 미승인이면 값 없음")
        Instant approvedAt,
        @Schema(description = "보유 자격증. 설정 저장은 선택 종목의 테스트 L1 자격증을 추가하며 기존 값은 보존")
        List<InstructorCertificateType> certificateTypes,
        @Schema(description = "리조트·종목·레벨·시간·인원·가격·노출 현재값")
        DevInstructorConfigurationResponse configuration,
        @Schema(description = "현재 상태에서 서버가 허용하는 동작")
        List<DevInstructorActionKey> availableActions,
        @Schema(description = "클릭 전후 경쟁 변경을 막는 상태 fingerprint")
        String stateToken,
        @Schema(description = "개발자가 현재 불완전·불일치 상태를 이해하기 위한 안내")
        List<String> diagnostics
) {
}
