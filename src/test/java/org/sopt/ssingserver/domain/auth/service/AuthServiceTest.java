package org.sopt.ssingserver.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sopt.ssingserver.domain.auth.client.KakaoOAuthClient;
import org.sopt.ssingserver.domain.auth.dto.response.AuthRefreshResponse;
import org.sopt.ssingserver.domain.auth.dto.result.IssuedAccessToken;
import org.sopt.ssingserver.domain.auth.entity.RefreshToken;
import org.sopt.ssingserver.domain.auth.repository.OAuthAccountRepository;
import org.sopt.ssingserver.domain.instructor.repository.InstructorProfileRepository;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.member.repository.MemberRepository;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-13T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Instant ALREADY_REVOKED_AT = NOW.minusSeconds(30);
    private static final String RAW_REFRESH_TOKEN = "raw-refresh-token";
    private static final String TOKEN_HASH = "a".repeat(64);

    @Mock
    private KakaoOAuthClient kakaoOAuthClient;

    @Mock
    private OAuthAccountRepository oauthAccountRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private InstructorProfileRepository instructorProfileRepository;

    @Mock
    private AuthTokenIssueService authTokenIssueService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private PlatformTransactionManager transactionManager;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                kakaoOAuthClient,
                oauthAccountRepository,
                memberRepository,
                instructorProfileRepository,
                authTokenIssueService,
                refreshTokenService,
                transactionManager,
                FIXED_CLOCK
        );
    }

    @Test
    void refreshAccessToken은_ACTIVE_회원에게_새_Access_Token을_반환한다() {
        Member member = member(MemberStatus.ACTIVE);
        RefreshToken refreshToken = refreshToken(member);
        IssuedAccessToken issuedAccessToken = IssuedAccessToken.of("new-access-token", "Bearer", 3600);
        when(refreshTokenService.findValidRefreshToken(RAW_REFRESH_TOKEN)).thenReturn(refreshToken);
        when(authTokenIssueService.issueAccessToken(member)).thenReturn(issuedAccessToken);

        AuthRefreshResponse result = authService.refreshAccessToken(RAW_REFRESH_TOKEN);

        assertThat(result).isEqualTo(new AuthRefreshResponse("new-access-token", "Bearer", 3600));
        verify(authTokenIssueService).issueAccessToken(member);
    }

    @ParameterizedTest
    @EnumSource(value = MemberStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
    void refreshAccessToken은_ACTIVE가_아닌_회원이면_FORBIDDEN이고_토큰을_발급하지_않는다(
            MemberStatus memberStatus
    ) {
        Member member = member(memberStatus);
        when(refreshTokenService.findValidRefreshToken(RAW_REFRESH_TOKEN))
                .thenReturn(refreshToken(member));

        assertThatThrownBy(() -> authService.refreshAccessToken(RAW_REFRESH_TOKEN))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isSameAs(CommonErrorCode.FORBIDDEN));
        verifyNoInteractions(authTokenIssueService);
    }

    @Test
    void logout은_폐기되지_않은_Refresh_Token을_현재시각에_폐기한다() {
        RefreshToken refreshToken = refreshToken(member(MemberStatus.ACTIVE));
        when(refreshTokenService.findRefreshTokenForLogout(RAW_REFRESH_TOKEN)).thenReturn(refreshToken);

        authService.logout(RAW_REFRESH_TOKEN);

        assertThat(refreshToken.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    void logout은_이미_폐기된_Refresh_Token의_폐기시각을_변경하지_않는다() {
        RefreshToken refreshToken = refreshToken(member(MemberStatus.ACTIVE));
        refreshToken.revoke(ALREADY_REVOKED_AT);
        when(refreshTokenService.findRefreshTokenForLogout(RAW_REFRESH_TOKEN)).thenReturn(refreshToken);

        authService.logout(RAW_REFRESH_TOKEN);

        assertThat(refreshToken.getRevokedAt()).isEqualTo(ALREADY_REVOKED_AT);
    }

    private RefreshToken refreshToken(Member member) {
        return RefreshToken.issue(member, TOKEN_HASH, NOW.plusSeconds(3600));
    }

    private Member member(MemberStatus status) {
        return Member.create("테스트회원", null, MemberRole.CONSUMER, status);
    }
}
