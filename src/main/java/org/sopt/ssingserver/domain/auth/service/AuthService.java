package org.sopt.ssingserver.domain.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import org.hibernate.exception.ConstraintViolationException;
import org.sopt.ssingserver.domain.auth.client.KakaoOAuthClient;
import org.sopt.ssingserver.domain.auth.client.KakaoProfile;
import org.sopt.ssingserver.domain.auth.client.KakaoTokenInfo;
import org.sopt.ssingserver.domain.auth.config.RefreshTokenProperties;
import org.sopt.ssingserver.domain.auth.dto.response.AuthRefreshResponse;
import org.sopt.ssingserver.domain.auth.dto.response.AuthLoginResult;
import org.sopt.ssingserver.domain.auth.dto.response.InstructorAuthLoginResult;
import org.sopt.ssingserver.domain.auth.dto.response.InstructorStatusResponse;
import org.sopt.ssingserver.domain.auth.entity.OAuthAccount;
import org.sopt.ssingserver.domain.auth.entity.RefreshToken;
import org.sopt.ssingserver.domain.auth.enums.OAuthProvider;
import org.sopt.ssingserver.domain.auth.error.AuthErrorCode;
import org.sopt.ssingserver.domain.auth.repository.OAuthAccountRepository;
import org.sopt.ssingserver.domain.auth.repository.RefreshTokenRepository;
import org.sopt.ssingserver.domain.auth.token.AccessTokenClaims;
import org.sopt.ssingserver.domain.auth.token.AccessTokenException;
import org.sopt.ssingserver.domain.auth.token.AccessTokenProperties;
import org.sopt.ssingserver.domain.auth.token.AccessTokenProvider;
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

    private static final String TOKEN_TYPE = "Bearer";
    private static final String REFRESH_TOKEN_PREFIX = "rt_";
    private static final String DEFAULT_NICKNAME_PREFIX = "스키어";
    private static final String OAUTH_PROVIDER_USER_UNIQUE_CONSTRAINT = "uk_oauth_accounts_provider_user";
    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final KakaoOAuthClient kakaoOAuthClient;
    private final OAuthAccountRepository oauthAccountRepository;
    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final InstructorProfileRepository instructorProfileRepository;
    private final AccessTokenProvider accessTokenProvider;
    private final AccessTokenProperties accessTokenProperties;
    private final RefreshTokenProperties refreshTokenProperties;
    private final TransactionTemplate transactionTemplate;
    private final SecureRandom secureRandom;
    private final Clock clock;

    public AuthService(
            KakaoOAuthClient kakaoOAuthClient,
            OAuthAccountRepository oauthAccountRepository,
            MemberRepository memberRepository,
            RefreshTokenRepository refreshTokenRepository,
            InstructorProfileRepository instructorProfileRepository,
            AccessTokenProvider accessTokenProvider,
            AccessTokenProperties accessTokenProperties,
            RefreshTokenProperties refreshTokenProperties,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this.kakaoOAuthClient = kakaoOAuthClient;
        this.oauthAccountRepository = oauthAccountRepository;
        this.memberRepository = memberRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.instructorProfileRepository = instructorProfileRepository;
        this.accessTokenProvider = accessTokenProvider;
        this.accessTokenProperties = accessTokenProperties;
        this.refreshTokenProperties = refreshTokenProperties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.secureRandom = new SecureRandom();
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

        String accessToken = accessTokenProvider.createAccessToken(member.getId(), member.getRole());
        String refreshToken = issueRefreshToken(member);

        return new AuthLoginResult(
                accessToken,
                refreshToken,
                TOKEN_TYPE,
                accessTokenProperties.accessTokenExpiration().toSeconds(),
                member.getId(),
                member.getNickname(),
                member.getRole(),
                member.getStatus()
        );
    }

    @Transactional(readOnly = true)
    public AuthRefreshResponse refreshAccessToken(String rawRefreshToken) {
        if (!StringUtils.hasText(rawRefreshToken)) {
            throw new BusinessException(AuthErrorCode.AUTH_INVALID_TOKEN);
        }

        // Refresh Token 원문 미저장을 위한 hash 기반 조회
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(sha256Hex(rawRefreshToken))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.AUTH_INVALID_TOKEN));

        if (refreshToken.isRevoked()) {
            throw new BusinessException(AuthErrorCode.AUTH_INVALID_TOKEN);
        }
        if (refreshToken.isExpired(Instant.now(clock))) {
            throw new BusinessException(AuthErrorCode.AUTH_TOKEN_EXPIRED);
        }

        Member member = refreshToken.getMember();
        validateActiveMember(member);

        String accessToken = accessTokenProvider.createAccessToken(member.getId(), member.getRole());
        return new AuthRefreshResponse(
                accessToken,
                TOKEN_TYPE,
                accessTokenProperties.accessTokenExpiration().toSeconds()
        );
    }

    @Transactional
    public void logout(String accessToken, String rawRefreshToken) {
        // 로그아웃 전용 만료 Access Token 허용
        AccessTokenClaims claims = parseAccessTokenForLogout(accessToken);
        if (!StringUtils.hasText(rawRefreshToken)) {
            throw new BusinessException(AuthErrorCode.AUTH_INVALID_TOKEN);
        }

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(sha256Hex(rawRefreshToken))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.AUTH_INVALID_TOKEN));

        if (!refreshToken.getMember().getId().equals(claims.memberId())) {
            throw new BusinessException(AuthErrorCode.AUTH_INVALID_TOKEN);
        }

        // 중복 로그아웃 허용
        if (!refreshToken.isRevoked()) {
            refreshToken.revoke(Instant.now(clock));
        }
    }

    private AccessTokenClaims parseAccessTokenForLogout(String accessToken) {
        try {
            return accessTokenProvider.parseAccessTokenAllowExpired(accessToken);
        } catch (AccessTokenException exception) {
            throw new BusinessException(exception.getErrorCode(), exception);
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

    private String issueRefreshToken(Member member) {
        String rawToken = createOpaqueToken();
        // DB 저장 전 Refresh Token hash 변환
        String tokenHash = sha256Hex(rawToken);
        RefreshToken refreshToken = RefreshToken.issue(
                member,
                tokenHash,
                Instant.now(clock).plus(refreshTokenProperties.expiration())
        );
        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    private String createOpaqueToken() {
        // 예측 불가능한 opaque Refresh Token 생성
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return REFRESH_TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            char[] hexChars = new char[hash.length * 2];
            for (int i = 0; i < hash.length; i++) {
                int value = hash[i] & 0xff;
                hexChars[i * 2] = HEX[value >>> 4];
                hexChars[i * 2 + 1] = HEX[value & 0x0f];
            }
            return new String(hexChars);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable.", exception);
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
