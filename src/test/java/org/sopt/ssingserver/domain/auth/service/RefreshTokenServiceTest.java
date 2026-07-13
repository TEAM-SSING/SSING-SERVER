package org.sopt.ssingserver.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sopt.ssingserver.domain.auth.config.RefreshTokenProperties;
import org.sopt.ssingserver.domain.auth.entity.RefreshToken;
import org.sopt.ssingserver.domain.auth.error.AuthErrorCode;
import org.sopt.ssingserver.domain.auth.repository.RefreshTokenRepository;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.global.error.BusinessException;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-13T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration EXPIRATION = Duration.ofDays(14);
    private static final String RAW_REFRESH_TOKEN = "raw-refresh-token";

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(
                refreshTokenRepository,
                new RefreshTokenProperties(EXPIRATION),
                FIXED_CLOCK
        );
    }

    @Test
    void issueRefreshToken은_원문을_반환하고_repository에는_hash만_저장한다() {
        Member member = activeMember();

        String rawRefreshToken = refreshTokenService.issueRefreshToken(member);

        ArgumentCaptor<RefreshToken> refreshTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());
        RefreshToken savedRefreshToken = refreshTokenCaptor.getValue();

        assertThat(rawRefreshToken).startsWith("rt_");
        assertThat(savedRefreshToken.getMember()).isSameAs(member);
        assertThat(savedRefreshToken.getTokenHash())
                .isNotEqualTo(rawRefreshToken)
                .isEqualTo(sha256Hex(rawRefreshToken));
        assertThat(savedRefreshToken.getExpiresAt()).isEqualTo(NOW.plus(EXPIRATION));
    }

    @Test
    void findValidRefreshToken은_원문을_hash로_조회해_유효한_토큰을_반환한다() {
        RefreshToken refreshToken = refreshToken(NOW.plusSeconds(1));
        when(refreshTokenRepository.findByTokenHash(sha256Hex(RAW_REFRESH_TOKEN)))
                .thenReturn(Optional.of(refreshToken));

        RefreshToken result = refreshTokenService.findValidRefreshToken(RAW_REFRESH_TOKEN);

        assertThat(result).isSameAs(refreshToken);
        assertThat(result.getRevokedAt()).isNull();
        verify(refreshTokenRepository).findByTokenHash(sha256Hex(RAW_REFRESH_TOKEN));
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void findRefreshTokenForLogout은_폐기된_토큰도_hash로_조회해_반환한다() {
        RefreshToken refreshToken = refreshToken(NOW.plusSeconds(1));
        refreshToken.revoke(NOW.minusSeconds(1));
        when(refreshTokenRepository.findByTokenHash(sha256Hex(RAW_REFRESH_TOKEN)))
                .thenReturn(Optional.of(refreshToken));

        RefreshToken result = refreshTokenService.findRefreshTokenForLogout(RAW_REFRESH_TOKEN);

        assertThat(result).isSameAs(refreshToken);
        verify(refreshTokenRepository).findByTokenHash(sha256Hex(RAW_REFRESH_TOKEN));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void findValidRefreshToken은_공백_원문이면_repository를_조회하지_않는다(String rawRefreshToken) {
        assertAuthError(
                () -> refreshTokenService.findValidRefreshToken(rawRefreshToken),
                AuthErrorCode.AUTH_INVALID_TOKEN
        );

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void findValidRefreshToken은_hash에_해당하는_토큰이_없으면_거부한다() {
        when(refreshTokenRepository.findByTokenHash(sha256Hex(RAW_REFRESH_TOKEN)))
                .thenReturn(Optional.empty());

        assertAuthError(
                () -> refreshTokenService.findValidRefreshToken(RAW_REFRESH_TOKEN),
                AuthErrorCode.AUTH_INVALID_TOKEN
        );
    }

    @Test
    void findValidRefreshToken은_폐기된_토큰을_거부한다() {
        RefreshToken refreshToken = refreshToken(NOW.plusSeconds(1));
        refreshToken.revoke(NOW.minusSeconds(1));
        when(refreshTokenRepository.findByTokenHash(sha256Hex(RAW_REFRESH_TOKEN)))
                .thenReturn(Optional.of(refreshToken));

        assertAuthError(
                () -> refreshTokenService.findValidRefreshToken(RAW_REFRESH_TOKEN),
                AuthErrorCode.AUTH_INVALID_TOKEN
        );
    }

    @Test
    void findValidRefreshToken은_현재시각에_만료된_토큰을_거부한다() {
        RefreshToken refreshToken = refreshToken(NOW);
        when(refreshTokenRepository.findByTokenHash(sha256Hex(RAW_REFRESH_TOKEN)))
                .thenReturn(Optional.of(refreshToken));

        assertAuthError(
                () -> refreshTokenService.findValidRefreshToken(RAW_REFRESH_TOKEN),
                AuthErrorCode.AUTH_TOKEN_EXPIRED
        );
    }

    private RefreshToken refreshToken(Instant expiresAt) {
        return RefreshToken.issue(activeMember(), sha256Hex(RAW_REFRESH_TOKEN), expiresAt);
    }

    private Member activeMember() {
        return Member.create("테스트회원", null, MemberRole.CONSUMER, MemberStatus.ACTIVE);
    }

    private void assertAuthError(Runnable action, AuthErrorCode expectedErrorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isSameAs(expectedErrorCode));
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
