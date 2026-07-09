package org.sopt.ssingserver.global.realtime;

import java.security.Principal;
import org.sopt.ssingserver.domain.member.enums.MemberRole;

public record RealtimePrincipal(
        Long memberId,
        MemberRole role
) implements Principal {

    // convertAndSendToUser의 사용자 식별 키와 맞추기 위해 Principal 이름을 memberId 문자열로 고정한다.
    @Override
    public String getName() {
        return memberId.toString();
    }
}
