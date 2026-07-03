package org.sopt.ssingserver.domain.auth.service;

import java.time.Clock;
import java.time.Instant;
import org.hibernate.exception.ConstraintViolationException;
import org.sopt.ssingserver.domain.auth.client.KakaoOAuthClient;
import org.sopt.ssingserver.domain.auth.client.KakaoProfile;
import org.sopt.ssingserver.domain.auth.client.KakaoTokenInfo;
import org.sopt.ssingserver.domain.auth.dto.response.AuthRefreshResponse;
import org.sopt.ssingserver.domain.auth.dto.response.AuthLoginResult;
import org.sopt.ssingserver.domain.auth.dto.response.InstructorAuthLoginResult;
import org.sopt.ssingserver.domain.auth.dto.response.InstructorStatusResponse;
import org.sopt.ssingserver.domain.auth.entity.OAuthAccount;
import org.sopt.ssingserver.domain.auth.entity.RefreshToken;
import org.sopt.ssingserver.domain.auth.enums.OAuthProvider;
import org.sopt.ssingserver.domain.auth.repository.OAuthAccountRepository;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.instructor.repository.InstructorProfileRepository;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.member.repository.MemberRepository;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private static final String DEFAULT_NICKNAME_PREFIX = "스키어";
    private static final String OAUTH_PROVIDER_USER_UNIQUE_CONSTRAINT = "uk_oauth_accounts_provider_user";

    private final KakaoOAuthClient kakaoOAuthClient;
    private final OAuthAccountRepository oauthAccountRepository;
    private final MemberRepository memberRepository;
    private final InstructorProfileRepository instructorProfileRepository;
    private final AuthTokenIssuer authTokenIssuer;
    private final RefreshTokenService refreshTokenService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public AuthService(
            KakaoOAuthClient kakaoOAuthClient,
            OAuthAccountRepository oauthAccountRepository,
            MemberRepository memberRepository,
            InstructorProfileRepository instructorProfileRepository,
            AuthTokenIssuer authTokenIssuer,
            RefreshTokenService refreshTokenService,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this.kakaoOAuthClient = kakaoOAuthClient;
        this.oauthAccountRepository = oauthAccountRepository;
        this.memberRepository = memberRepository;
        this.instructorProfileRepository = instructorProfileRepository;
        this.authTokenIssuer = authTokenIssuer;
        this.refreshTokenService = refreshTokenService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public AuthLoginResult loginConsumerWithKakao(String kakaoAccessToken) {
        return loginWithKakao(kakaoAccessToken);
    }

    public InstructorAuthLoginResult loginInstructorWithKakao(String kakaoAccessToken) {
        AuthLoginResult loginResult = loginWithKakao(kakaoAccessToken);
        return new InstructorAuthLoginResult(
                loginResult,
                resolveInstructorStatus(loginResult.memberId())
        );
    }

    private AuthLoginResult loginWithKakao(String kakaoAccessToken) {
        // 카카오 토큰 검증 후 신규 회원 프로필 조회
        KakaoTokenInfo tokenInfo = kakaoOAuthClient.validateAccessToken(kakaoAccessToken);
        KakaoProfile kakaoProfile = loadProfileIfNewMember(tokenInfo.providerUserId(), kakaoAccessToken);

        // 외부 API 호출 이후 DB 쓰기 트랜잭션 경계
        try {
            return transactionTemplate.execute(status -> loginWithKakaoInTransaction(tokenInfo.providerUserId(), kakaoProfile));
        } catch (DataIntegrityViolationException exception) {
            if (!isOAuthProviderUserConflict(exception)) {
                throw exception;
            }
            return recoverConcurrentLogin(tokenInfo.providerUserId(), exception);
        }
    }

    private KakaoProfile loadProfileIfNewMember(String providerUserId, String kakaoAccessToken) {
        if (oauthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, providerUserId).isPresent()) {
            return null;
        }
        return kakaoOAuthClient.getProfile(kakaoAccessToken);
    }

    private AuthLoginResult loginWithKakaoInTransaction(String providerUserId, KakaoProfile kakaoProfile) {
        Member member = findOrCreateMember(providerUserId, kakaoProfile);
        return issueLoginResult(member);
    }

    private AuthLoginResult recoverConcurrentLogin(
            String providerUserId,
            DataIntegrityViolationException exception
    ) {
        // 동시에 들어온 첫 로그인 중 하나가 oauth_accounts unique 제약에서 밀린 경우만 복구한다.
        return transactionTemplate.execute(status -> oauthAccountRepository.findByProviderAndProviderUserId(
                        OAuthProvider.KAKAO,
                        providerUserId
                )
                .map(OAuthAccount::getMember)
                .map(this::issueLoginResult)
                .orElseThrow(() -> exception));
    }

    private boolean isOAuthProviderUserConflict(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolationException
                    && OAUTH_PROVIDER_USER_UNIQUE_CONSTRAINT.equals(constraintViolationException.getConstraintName())) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.contains(OAUTH_PROVIDER_USER_UNIQUE_CONSTRAINT)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private AuthLoginResult issueLoginResult(Member member) {
        validateActiveMember(member);

        IssuedAuthTokens tokens = authTokenIssuer.issueTokens(member);

        return new AuthLoginResult(
                tokens.accessToken(),
                tokens.refreshToken(),
                tokens.tokenType(),
                tokens.expiresIn(),
                member.getId(),
                member.getNickname(),
                member.getRole(),
                member.getStatus()
        );
    }

    @Transactional(readOnly = true)
    public AuthRefreshResponse refreshAccessToken(String rawRefreshToken) {
        RefreshToken refreshToken = refreshTokenService.findValidRefreshToken(rawRefreshToken);
        Member member = refreshToken.getMember();
        validateActiveMember(member);

        IssuedAccessToken accessToken = authTokenIssuer.issueAccessToken(member);
        return new AuthRefreshResponse(
                accessToken.accessToken(),
                accessToken.tokenType(),
                accessToken.expiresIn()
        );
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        RefreshToken refreshToken = refreshTokenService.findRefreshTokenForLogout(rawRefreshToken);

        // 중복 로그아웃 허용
        if (!refreshToken.isRevoked()) {
            refreshToken.revoke(Instant.now(clock));
        }
    }

    private Member findOrCreateMember(String providerUserId, KakaoProfile kakaoProfile) {
        return oauthAccountRepository.findByProviderAndProviderUserId(
                        OAuthProvider.KAKAO,
                        providerUserId
                )
                .map(OAuthAccount::getMember)
                .orElseGet(() -> createMemberWithOAuthAccount(providerUserId, kakaoProfile));
    }

    private Member createMemberWithOAuthAccount(String providerUserId, KakaoProfile kakaoProfile) {
        KakaoProfile profileForCreation = kakaoProfile != null ? kakaoProfile : new KakaoProfile(providerUserId, null, null);
        Member member = memberRepository.save(Member.createOAuthMember(
                resolveNickname(profileForCreation, providerUserId),
                profileForCreation.profileImageUrl()
        ));
        oauthAccountRepository.save(OAuthAccount.create(
                member,
                OAuthProvider.KAKAO,
                providerUserId
        ));
        return member;
    }

    private String resolveNickname(KakaoProfile kakaoProfile, String providerUserId) {
        if (StringUtils.hasText(kakaoProfile.nickname())) {
            return kakaoProfile.nickname();
        }
        return DEFAULT_NICKNAME_PREFIX + providerUserId;
    }

    private void validateActiveMember(Member member) {
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }

    private InstructorStatusResponse resolveInstructorStatus(Long memberId) {
        // 강사 프로필 없음 응답 계산값
        return instructorProfileRepository.findByMemberId(memberId)
                .map(InstructorProfile::getApprovalStatus)
                .map(InstructorStatusResponse::from)
                .orElse(InstructorStatusResponse.NONE);
    }
}
