package org.sopt.ssingserver.domain.auth.client;

public record KakaoProfile(
        String providerUserId,
        String nickname,
        String profileImageUrl
) {
}
