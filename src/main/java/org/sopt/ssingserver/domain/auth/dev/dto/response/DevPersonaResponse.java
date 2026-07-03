package org.sopt.ssingserver.domain.auth.dev.dto.response;

import org.sopt.ssingserver.domain.auth.dev.enums.DevPersonaTemplate;
import org.sopt.ssingserver.domain.auth.dto.response.InstructorStatusResponse;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;

public record DevPersonaResponse(
        String personaKey,
        String nickname,
        DevPersonaTemplate template,
        MemberRole role,
        MemberStatus memberStatus,
        InstructorStatusResponse instructorStatus
) {
}
