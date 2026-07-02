package org.sopt.ssingserver.global.security;

import org.sopt.ssingserver.domain.member.enums.MemberRole;

public record AuthenticatedMember(
        Long memberId,
        MemberRole role
) {
}
