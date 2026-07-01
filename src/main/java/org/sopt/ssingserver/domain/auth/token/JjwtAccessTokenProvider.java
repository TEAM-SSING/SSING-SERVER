package org.sopt.ssingserver.domain.auth.token;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.sopt.ssingserver.domain.auth.error.AuthErrorCode;
import org.sopt.ssingserver.domain.member.enums.MemberRole;

public class JjwtAccessTokenProvider implements AccessTokenProvider {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String ACCESS_TOKEN_TYPE = "ACCESS";

    private final AccessTokenProperties properties;
    private final SecretKey secretKey;
    private final Clock clock;

    public JjwtAccessTokenProvider(
            AccessTokenProperties properties,
            Clock clock
    ) {
        this.properties = properties;
        this.secretKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    @Override
    public String createAccessToken(Long memberId, MemberRole role) {
        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plus(properties.accessTokenExpiration());

        // Access Token 최소 claim
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(memberId))
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_TOKEN_TYPE, ACCESS_TOKEN_TYPE)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    @Override
    public AccessTokenClaims parseAccessToken(String token) {
        if (token == null || token.isBlank()) {
            throw new AccessTokenException(AuthErrorCode.AUTH_INVALID_TOKEN);
        }

        try {
            // JJWT 내부 구현 경계
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(properties.issuer())
                    .require(CLAIM_TOKEN_TYPE, ACCESS_TOKEN_TYPE)
                    .clock(() -> Date.from(Instant.now(clock)))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return new AccessTokenClaims(
                    readMemberId(claims),
                    readRole(claims),
                    readIssuedAt(claims),
                    readExpiresAt(claims)
            );
        } catch (ExpiredJwtException exception) {
            // 만료 Access Token 에러 분기
            throw new AccessTokenException(AuthErrorCode.AUTH_TOKEN_EXPIRED, exception);
        } catch (JwtException | IllegalArgumentException exception) {
            // JJWT 예외의 서비스 에러 코드 변환
            throw new AccessTokenException(AuthErrorCode.AUTH_INVALID_TOKEN, exception);
        }
    }

    private Long readMemberId(Claims claims) {
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new AccessTokenException(AuthErrorCode.AUTH_INVALID_TOKEN);
        }
        return Long.valueOf(subject);
    }

    private MemberRole readRole(Claims claims) {
        String role = claims.get(CLAIM_ROLE, String.class);
        if (role == null || role.isBlank()) {
            throw new AccessTokenException(AuthErrorCode.AUTH_INVALID_TOKEN);
        }
        return MemberRole.valueOf(role);
    }

    private Instant readIssuedAt(Claims claims) {
        Date issuedAt = claims.getIssuedAt();
        if (issuedAt == null) {
            throw new AccessTokenException(AuthErrorCode.AUTH_INVALID_TOKEN);
        }
        return issuedAt.toInstant();
    }

    private Instant readExpiresAt(Claims claims) {
        Date expiresAt = claims.getExpiration();
        if (expiresAt == null) {
            throw new AccessTokenException(AuthErrorCode.AUTH_INVALID_TOKEN);
        }
        return expiresAt.toInstant();
    }
}
