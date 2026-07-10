package org.sopt.ssingserver.domain.auth.dev.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.sopt.ssingserver.domain.auth.dev.enums.DevPersonaTemplate;
import org.sopt.ssingserver.domain.auth.dto.response.InstructorStatusResponse;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;

@Schema(description = "토큰 발급 시점의 개발용 persona 상태")
public record DevPersonaSnapshotResponse(
        @Schema(description = "개발용 persona key", example = "consumer-matching-a")
        String personaKey,

        @Schema(description = "합성 회원 닉네임", example = "매칭 강습생 A")
        String nickname,

        @Schema(description = "persona 상태 템플릿", example = "GENERAL_CONSUMER")
        DevPersonaTemplate template,

        @Schema(description = "회원 역할", example = "CONSUMER")
        MemberRole role,

        @Schema(description = "회원 상태", example = "ACTIVE")
        MemberStatus memberStatus,

        @Schema(description = "강사 프로필이 있을 때의 승인 상태", example = "NONE")
        InstructorStatusResponse instructorStatus
) {

    public static DevPersonaSnapshotResponse from(DevPersonaResponse response) {
        return new DevPersonaSnapshotResponse(
                response.personaKey(),
                response.nickname(),
                response.template(),
                response.role(),
                response.memberStatus(),
                response.instructorStatus()
        );
    }
}
