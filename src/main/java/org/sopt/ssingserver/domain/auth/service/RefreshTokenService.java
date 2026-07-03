package org.sopt.ssingserver.domain.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.sopt.ssingserver.domain.auth.config.RefreshTokenProperties;
import org.sopt.ssingserver.domain.auth.entity.RefreshToken;
import org.sopt.ssingserver.domain.auth.error.AuthErrorCode;
import org.sopt.ssingserver.domain.auth.repository.RefreshTokenRepository;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.global.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RefreshTokenService {

    private static final String REFRESH_TOKEN_PREFIX = "rt_";
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenProperties refreshTokenProperties;
    private final SecureRandom secureRandom;
    private final Clock clock;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            RefreshTokenProperties refreshTokenProperties,
            Clock clock
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenProperties = refreshTokenProperties;
        this.secureRandom = new SecureRandom();
        this.clock = clock;
    }

    public String issueRefreshToken(Member member) {
        String rawToken = createOpaqueToken();
        // Refresh Token 원문을 저장하지 않기 위한 hash 변환
        String tokenHash = sha256Hex(rawToken);
        RefreshToken refreshToken = RefreshToken.issue(
                member,
                tokenHash,
                Instant.now(clock).plus(refreshTokenProperties.expiration())
        );
        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    public RefreshToken findValidRefreshToken(String rawRefreshToken) {
        RefreshToken refreshToken = findRefreshToken(rawRefreshToken);
        if (refreshToken.isRevoked()) {
            throw new BusinessException(AuthErrorCode.AUTH_INVALID_TOKEN);
        }
        if (refreshToken.isExpired(Instant.now(clock))) {
            throw new BusinessException(AuthErrorCode.AUTH_TOKEN_EXPIRED);
        }
        return refreshToken;
    }

    public RefreshToken findRefreshTokenForLogout(String rawRefreshToken) {
        return findRefreshToken(rawRefreshToken);
    }

    private RefreshToken findRefreshToken(String rawRefreshToken) {
        if (!StringUtils.hasText(rawRefreshToken)) {
            throw new BusinessException(AuthErrorCode.AUTH_INVALID_TOKEN);
        }
        return refreshTokenRepository.findByTokenHash(sha256Hex(rawRefreshToken))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.AUTH_INVALID_TOKEN));
    }

    private String createOpaqueToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return REFRESH_TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable.", exception);
        }
    }
}
