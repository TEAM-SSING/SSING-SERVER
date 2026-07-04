package org.sopt.ssingserver.domain.auth.dev.dto.response;

import java.time.Instant;
import org.sopt.ssingserver.domain.auth.dev.entity.DevPersona;
import org.sopt.ssingserver.domain.auth.dev.enums.DevPersonaTemplate;
import org.sopt.ssingserver.domain.auth.dto.response.InstructorStatusResponse;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;

public record DevPersonaResponse(
        String personaKey,
        String nickname,
        DevPersonaTemplate template,
        MemberRole role,
        MemberStatus memberStatus,
        InstructorStatusResponse instructorStatus,
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
