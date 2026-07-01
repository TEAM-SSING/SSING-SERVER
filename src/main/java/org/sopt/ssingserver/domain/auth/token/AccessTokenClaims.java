package org.sopt.ssingserver.domain.auth.token;

import java.time.Instant;
import org.sopt.ssingserver.domain.member.enums.MemberRole;

public record AccessTokenClaims(
        Long memberId,
        MemberRole role,
        Instant issuedAt,
        Instant expiresAt
) {
}
