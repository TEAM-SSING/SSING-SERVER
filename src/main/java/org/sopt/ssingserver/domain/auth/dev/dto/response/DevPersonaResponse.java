package org.sopt.ssingserver.domain.auth.dev.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.sopt.ssingserver.domain.auth.dev.entity.DevPersona;
import org.sopt.ssingserver.domain.auth.dev.enums.DevPersonaTemplate;
import org.sopt.ssingserver.domain.auth.dto.response.InstructorStatusResponse;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;

@Schema(description = "개발용 persona 정보")
public record DevPersonaResponse(
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
        InstructorStatusResponse instructorStatus,

        @Schema(description = "persona 생성 시각", example = "2026-07-11T09:00:00Z")
        Instant createdAt
) {

    public static DevPersonaResponse from(
            DevPersona devPersona,
            InstructorStatusResponse instructorStatus
    ) {
        Member member = devPersona.getMember();
        DevPersonaTemplate template = devPersona.getTemplate();
        return new DevPersonaResponse(
                devPersona.getPersonaKey(),
                member.getNickname(),
                template,
                member.getRole(),
                member.getStatus(),
                instructorStatus,
                devPersona.getCreatedAt()
        );
    }
}
