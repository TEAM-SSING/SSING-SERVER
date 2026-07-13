package org.sopt.ssingserver.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sopt.ssingserver.domain.auth.client.KakaoProfile;
import org.sopt.ssingserver.domain.auth.client.KakaoTokenInfo;
import org.sopt.ssingserver.domain.auth.client.KakaoOAuthClient;
import org.sopt.ssingserver.domain.auth.dto.response.AuthLoginResult;
import org.sopt.ssingserver.domain.auth.dto.response.AuthRefreshResponse;
import org.sopt.ssingserver.domain.auth.dto.response.InstructorAuthLoginResult;
import org.sopt.ssingserver.domain.auth.dto.response.InstructorStatusResponse;
import org.sopt.ssingserver.domain.auth.dto.result.IssuedAccessToken;
import org.sopt.ssingserver.domain.auth.dto.result.IssuedAuthTokens;
import org.sopt.ssingserver.domain.auth.entity.OAuthAccount;
import org.sopt.ssingserver.domain.auth.entity.RefreshToken;
import org.sopt.ssingserver.domain.auth.enums.OAuthProvider;
import org.sopt.ssingserver.domain.auth.repository.OAuthAccountRepository;
import org.sopt.ssingserver.domain.instructor.repository.InstructorProfileRepository;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.member.repository.MemberRepository;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-13T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Instant ALREADY_REVOKED_AT = NOW.minusSeconds(30);
    private static final String RAW_REFRESH_TOKEN = "raw-refresh-token";
    private static final String TOKEN_HASH = "a".repeat(64);
    private static final String KAKAO_ACCESS_TOKEN = "kakao-access-token";
    private static final String PROVIDER_USER_ID = "123456789";
    private static final IssuedAuthTokens ISSUED_AUTH_TOKENS = IssuedAuthTokens.of(
            "access-token",
            "refresh-token",
            "Bearer",
            3600
    );

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
                new NoOpTransactionManager(),
                FIXED_CLOCK
        );
    }

    @Test
    void loginConsumerWithKakao는_기존_OAuth_회원이면_프로필을_다시_조회하거나_회원을_생성하지_않는다() {
        Member member = member(1L, MemberStatus.ACTIVE);
        OAuthAccount oauthAccount = OAuthAccount.create(member, OAuthProvider.KAKAO, PROVIDER_USER_ID);
        when(kakaoOAuthClient.validateAccessToken(KAKAO_ACCESS_TOKEN))
                .thenReturn(new KakaoTokenInfo(PROVIDER_USER_ID));
        when(oauthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, PROVIDER_USER_ID))
                .thenReturn(Optional.of(oauthAccount));
        when(authTokenIssueService.issueTokens(member)).thenReturn(ISSUED_AUTH_TOKENS);

        AuthLoginResult result = authService.loginConsumerWithKakao(KAKAO_ACCESS_TOKEN);

        assertThat(result).isEqualTo(new AuthLoginResult(
                "access-token",
                "refresh-token",
                "Bearer",
                3600,
                1L,
                "테스트회원",
                MemberRole.CONSUMER,
                MemberStatus.ACTIVE
        ));
        verify(kakaoOAuthClient, never()).getProfile(anyString());
        verify(memberRepository, never()).save(any(Member.class));
        verify(oauthAccountRepository, never()).save(any(OAuthAccount.class));
    }

    @Test
    void loginInstructorWithKakao는_신규_회원과_OAuth_계정을_만들고_강사상태_NONE을_반환한다() {
        KakaoProfile kakaoProfile = new KakaoProfile(
                PROVIDER_USER_ID,
                "신규 강사",
                "https://example.com/profile.png"
        );
        when(kakaoOAuthClient.validateAccessToken(KAKAO_ACCESS_TOKEN))
                .thenReturn(new KakaoTokenInfo(PROVIDER_USER_ID));
        when(oauthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, PROVIDER_USER_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(kakaoOAuthClient.getProfile(KAKAO_ACCESS_TOKEN)).thenReturn(kakaoProfile);
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> {
            Member savedMember = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedMember, "id", 2L);
            return savedMember;
        });
        when(oauthAccountRepository.save(any(OAuthAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(authTokenIssueService.issueTokens(any(Member.class))).thenReturn(ISSUED_AUTH_TOKENS);
        when(instructorProfileRepository.findByMemberId(2L)).thenReturn(Optional.empty());

        InstructorAuthLoginResult result = authService.loginInstructorWithKakao(KAKAO_ACCESS_TOKEN);

        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        ArgumentCaptor<OAuthAccount> oauthAccountCaptor = ArgumentCaptor.forClass(OAuthAccount.class);
        verify(memberRepository).save(memberCaptor.capture());
        verify(oauthAccountRepository).save(oauthAccountCaptor.capture());
        Member savedMember = memberCaptor.getValue();
        OAuthAccount savedOAuthAccount = oauthAccountCaptor.getValue();
        assertThat(savedMember.getId()).isEqualTo(2L);
        assertThat(savedMember.getNickname()).isEqualTo("신규 강사");
        assertThat(savedMember.getProfileImageUrl()).isEqualTo("https://example.com/profile.png");
        assertThat(savedMember.getRole()).isSameAs(MemberRole.CONSUMER);
        assertThat(savedMember.getStatus()).isSameAs(MemberStatus.ACTIVE);
        assertThat(savedOAuthAccount.getMember()).isSameAs(savedMember);
        assertThat(savedOAuthAccount.getProvider()).isSameAs(OAuthProvider.KAKAO);
        assertThat(savedOAuthAccount.getProviderUserId()).isEqualTo(PROVIDER_USER_ID);
        assertThat(result.loginResult().memberId()).isEqualTo(2L);
        assertThat(result.loginResult().nickname()).isEqualTo("신규 강사");
        assertThat(result.instructorStatus()).isSameAs(InstructorStatusResponse.NONE);
    }

    @Test
    void loginConsumerWithKakao는_비활성_회원이면_FORBIDDEN이고_토큰을_발급하지_않는다() {
        Member member = member(1L, MemberStatus.SUSPENDED);
        OAuthAccount oauthAccount = OAuthAccount.create(member, OAuthProvider.KAKAO, PROVIDER_USER_ID);
        when(kakaoOAuthClient.validateAccessToken(KAKAO_ACCESS_TOKEN))
                .thenReturn(new KakaoTokenInfo(PROVIDER_USER_ID));
        when(oauthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, PROVIDER_USER_ID))
                .thenReturn(Optional.of(oauthAccount));

        assertThatThrownBy(() -> authService.loginConsumerWithKakao(KAKAO_ACCESS_TOKEN))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isSameAs(CommonErrorCode.FORBIDDEN));
        verifyNoInteractions(authTokenIssueService);
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    void loginConsumerWithKakao는_동시_최초로그인_unique_충돌이면_이미_생성된_회원으로_복구한다() {
        Member existingMember = member(1L, MemberStatus.ACTIVE);
        OAuthAccount existingAccount = OAuthAccount.create(
                existingMember,
                OAuthProvider.KAKAO,
                PROVIDER_USER_ID
        );
        when(kakaoOAuthClient.validateAccessToken(KAKAO_ACCESS_TOKEN))
                .thenReturn(new KakaoTokenInfo(PROVIDER_USER_ID));
        when(oauthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, PROVIDER_USER_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingAccount));
        when(kakaoOAuthClient.getProfile(KAKAO_ACCESS_TOKEN))
                .thenReturn(new KakaoProfile(PROVIDER_USER_ID, "동시 가입 회원", null));
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> {
            Member losingMember = invocation.getArgument(0);
            ReflectionTestUtils.setField(losingMember, "id", 2L);
            return losingMember;
        });
        when(oauthAccountRepository.save(any(OAuthAccount.class)))
                .thenThrow(new DataIntegrityViolationException("uk_oauth_accounts_provider_user"));
        when(authTokenIssueService.issueTokens(existingMember)).thenReturn(ISSUED_AUTH_TOKENS);

        AuthLoginResult result = authService.loginConsumerWithKakao(KAKAO_ACCESS_TOKEN);

        assertThat(result.memberId()).isEqualTo(1L);
        assertThat(result.nickname()).isEqualTo("테스트회원");
        verify(authTokenIssueService).issueTokens(existingMember);
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

    private Member member(Long id, MemberStatus status) {
        Member member = member(status);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    // TransactionTemplate 콜백 경계만 실행하는 단위 테스트용 더미다.
    private static class NoOpTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
