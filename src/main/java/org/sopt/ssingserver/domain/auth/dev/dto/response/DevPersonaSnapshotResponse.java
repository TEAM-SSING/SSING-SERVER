package org.sopt.ssingserver.domain.auth.dev.dto.response;

import org.sopt.ssingserver.domain.auth.dev.enums.DevPersonaTemplate;
import org.sopt.ssingserver.domain.auth.dto.response.InstructorStatusResponse;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;

public record DevPersonaSnapshotResponse(
        String personaKey,
        String nickname,
        DevPersonaTemplate template,
        MemberRole role,
        MemberStatus memberStatus,
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
