package org.sopt.ssingserver.domain.auth.client;

public interface KakaoOAuthClient {

    KakaoTokenInfo validateAccessToken(String kakaoAccessToken);

    KakaoProfile getProfile(String kakaoAccessToken);
}
