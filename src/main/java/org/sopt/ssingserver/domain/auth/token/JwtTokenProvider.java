package org.sopt.ssingserver.domain.auth.token;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.sopt.ssingserver.domain.auth.error.AuthErrorCode;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtEncodingException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

public class JwtTokenProvider {

    private static final String SECRET_KEY_ALGORITHM = "HmacSHA256";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TOKEN_TYPE = "tokenType";

    private final JwtProperties properties;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final Clock clock;

    public JwtTokenProvider(
            JwtProperties properties,
            Clock clock
    ) {
        this.properties = properties;
        this.clock = clock;

        SecretKey secretKey = createSecretKey(properties.secret());
        this.jwtEncoder = NimbusJwtEncoder.withSecretKey(secretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
        NimbusJwtDecoder nimbusJwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .validateType(true)
                .build();
        // 기본 claim validator 비활성화: 만료/issuer/tokenType은 서비스 에러 코드에 맞춰 직접 분기한다.
        nimbusJwtDecoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());
        this.jwtDecoder = nimbusJwtDecoder;
    }

    public String createAccessToken(Long memberId, MemberRole role) {
        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plus(properties.accessTokenExpiration());

        // Access Token 최소 claim 구성
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(memberId))
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_TOKEN_TYPE, TokenType.ACCESS.name())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .type("JWT")
                .build();

        try {
            return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        } catch (JwtEncodingException exception) {
            throw new IllegalStateException("Failed to encode access token.", exception);
        }
    }

    public JwtClaims parseAccessToken(String token) {
        try {
            if (token == null || token.isBlank()) {
                throw new JwtTokenException(AuthErrorCode.AUTH_INVALID_TOKEN);
            }

            // 서명 및 형식 검증
            Jwt jwt = jwtDecoder.decode(token);

            // Access Token 정책 검증
            validateIssuer(jwt);
            validateTokenType(jwt);

            Instant expiresAt = jwt.getExpiresAt();
            if (expiresAt == null) {
                throw new JwtTokenException(AuthErrorCode.AUTH_INVALID_TOKEN);
            }

            // 만료 토큰 에러 분기
            if (!Instant.now(clock).isBefore(expiresAt)) {
                throw new JwtTokenException(AuthErrorCode.AUTH_TOKEN_EXPIRED);
            }

            Instant issuedAt = jwt.getIssuedAt();
            if (issuedAt == null) {
                throw new JwtTokenException(AuthErrorCode.AUTH_INVALID_TOKEN);
            }

            return new JwtClaims(
                    readMemberId(jwt),
                    readRole(jwt),
                    TokenType.ACCESS,
                    issuedAt,
                    expiresAt
            );
        } catch (JwtTokenException exception) {
            throw exception;
        } catch (JwtException | IllegalArgumentException exception) {
            throw new JwtTokenException(AuthErrorCode.AUTH_INVALID_TOKEN, exception);
        }
    }

    private void validateIssuer(Jwt jwt) {
        if (!properties.issuer().equals(jwt.getClaimAsString("iss"))) {
            throw new JwtTokenException(AuthErrorCode.AUTH_INVALID_TOKEN);
        }
    }

    private void validateTokenType(Jwt jwt) {
        if (!TokenType.ACCESS.name().equals(jwt.getClaimAsString(CLAIM_TOKEN_TYPE))) {
            throw new JwtTokenException(AuthErrorCode.AUTH_INVALID_TOKEN);
        }
    }

    private Long readMemberId(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new JwtTokenException(AuthErrorCode.AUTH_INVALID_TOKEN);
        }
        return Long.valueOf(subject);
    }

    private MemberRole readRole(Jwt jwt) {
        String role = jwt.getClaimAsString(CLAIM_ROLE);
        if (role == null || role.isBlank()) {
            throw new JwtTokenException(AuthErrorCode.AUTH_INVALID_TOKEN);
        }
        return MemberRole.valueOf(role);
    }

    private SecretKey createSecretKey(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), SECRET_KEY_ALGORITHM);
    }
}
