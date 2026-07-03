package org.sopt.ssingserver.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.global.entity.BaseTimeEntity;

@Getter
@Entity
@Table(name = "refresh_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseTimeEntity {

    private static final int SHA_256_HEX_LENGTH = 64;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Member member;

    @Column(nullable = false, length = SHA_256_HEX_LENGTH)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    public static RefreshToken issue(Member member, String tokenHash, Instant expiresAt) {
        validateTokenHash(tokenHash);
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.member = member;
        refreshToken.tokenHash = tokenHash;
        refreshToken.expiresAt = expiresAt;
        return refreshToken;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public void revoke(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    private static void validateTokenHash(String tokenHash) {
        // Refresh Token 원문 저장 방지용 hash 형식 검증
        if (tokenHash == null || tokenHash.length() != SHA_256_HEX_LENGTH) {
            throw new IllegalArgumentException("Refresh token hash must be a SHA-256 hex digest.");
        }

        for (int i = 0; i < tokenHash.length(); i++) {
            char value = tokenHash.charAt(i);
            if (!isLowercaseHex(value)) {
                throw new IllegalArgumentException("Refresh token hash must be a SHA-256 hex digest.");
            }
        }
    }

    private static boolean isLowercaseHex(char value) {
        return ('0' <= value && value <= '9') || ('a' <= value && value <= 'f');
    }
}
