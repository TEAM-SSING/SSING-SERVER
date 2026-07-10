package org.sopt.ssingserver.domain.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;

class RefreshTokenTest {

    private static final String VALID_TOKEN_HASH = "0123456789abcdef".repeat(4);
    private static final Instant EXPIRES_AT = Instant.parse("2026-07-10T12:00:00Z");

    @Test
    void issue는_SHA256_hash와_만료시각을_저장하고_미폐기상태로_생성한다() {
        Member member = activeMember();

        RefreshToken refreshToken = RefreshToken.issue(member, VALID_TOKEN_HASH, EXPIRES_AT);

        assertThat(refreshToken.getMember()).isSameAs(member);
        assertThat(refreshToken.getTokenHash()).isEqualTo(VALID_TOKEN_HASH);
        assertThat(refreshToken.getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(refreshToken.getRevokedAt()).isNull();
        assertThat(refreshToken.isRevoked()).isFalse();
    }

    @Test
    void isExpired는_만료시각_직전에는_false이고_같거나_지난시각에는_true이다() {
        RefreshToken refreshToken = RefreshToken.issue(activeMember(), VALID_TOKEN_HASH, EXPIRES_AT);

        assertThat(refreshToken.isExpired(EXPIRES_AT.minusNanos(1))).isFalse();
        assertThat(refreshToken.isExpired(EXPIRES_AT)).isTrue();
        assertThat(refreshToken.isExpired(EXPIRES_AT.plusNanos(1))).isTrue();
    }

    @Test
    void revoke는_폐기시각을_저장하고_폐기상태로_변경한다() {
        RefreshToken refreshToken = RefreshToken.issue(activeMember(), VALID_TOKEN_HASH, EXPIRES_AT);
        Instant revokedAt = EXPIRES_AT.minusSeconds(60);

        refreshToken.revoke(revokedAt);

        assertThat(refreshToken.isRevoked()).isTrue();
        assertThat(refreshToken.getRevokedAt()).isEqualTo(revokedAt);
    }

    @ParameterizedTest
    @MethodSource("invalidTokenHashes")
    void issue는_SHA256_소문자_hex_형식이_아니면_생성하지_않는다(String tokenHash) {
        assertThatThrownBy(() -> RefreshToken.issue(activeMember(), tokenHash, EXPIRES_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Refresh token hash must be a SHA-256 hex digest.");
    }

    private static Stream<String> invalidTokenHashes() {
        return Stream.of(
                (String) null,
                "",
                "a".repeat(63),
                "a".repeat(65),
                "A".repeat(64),
                "g".repeat(64)
        );
    }

    private Member activeMember() {
        return Member.create("토큰회원", null, MemberRole.CONSUMER, MemberStatus.ACTIVE);
    }
}
