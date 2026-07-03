package org.sopt.ssingserver.domain.auth.service;

import org.sopt.ssingserver.domain.auth.token.AccessTokenProperties;
import org.sopt.ssingserver.domain.auth.token.AccessTokenProvider;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.springframework.stereotype.Service;

@Service
public class AuthTokenIssuer {

    private static final String TOKEN_TYPE = "Bearer";

    private final AccessTokenProvider accessTokenProvider;
    private final AccessTokenProperties accessTokenProperties;
    private final RefreshTokenService refreshTokenService;

    public AuthTokenIssuer(
            AccessTokenProvider accessTokenProvider,
            AccessTokenProperties accessTokenProperties,
            RefreshTokenService refreshTokenService
    ) {
        this.accessTokenProvider = accessTokenProvider;
        this.accessTokenProperties = accessTokenProperties;
        this.refreshTokenService = refreshTokenService;
    }

    public IssuedAuthTokens issueTokens(Member member) {
        IssuedAccessToken accessToken = issueAccessToken(member);
        String refreshToken = refreshTokenService.issueRefreshToken(member);
        return new IssuedAuthTokens(
                accessToken.accessToken(),
                refreshToken,
                accessToken.tokenType(),
                accessToken.expiresIn()
        );
    }

    public IssuedAccessToken issueAccessToken(Member member) {
        return new IssuedAccessToken(
                accessTokenProvider.createAccessToken(member.getId(), member.getRole()),
                TOKEN_TYPE,
                accessTokenProperties.accessTokenExpiration().toSeconds()
        );
    }
}
