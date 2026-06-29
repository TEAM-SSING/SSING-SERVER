package org.sopt.ssingserver.domain.auth.token;

import java.time.Instant;
import org.sopt.ssingserver.domain.member.enums.MemberRole;

public record JwtClaims(
        Long memberId,
        MemberRole role,
        TokenType tokenType,
        Instant issuedAt,
        Instant expiresAt
) {
}
