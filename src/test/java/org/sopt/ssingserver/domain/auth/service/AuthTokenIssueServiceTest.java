package org.sopt.ssingserver.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sopt.ssingserver.domain.auth.dto.result.IssuedAccessToken;
import org.sopt.ssingserver.domain.auth.token.AccessTokenProperties;
import org.sopt.ssingserver.domain.auth.token.AccessTokenProvider;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthTokenIssueServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final String ACCESS_TOKEN = "access-token";
    private static final Duration ACCESS_TOKEN_EXPIRATION = Duration.ofHours(1);

    @Mock
    private AccessTokenProvider accessTokenProvider;

    @Mock
    private RefreshTokenService refreshTokenService;

    private AuthTokenIssueService authTokenIssueService;

    @BeforeEach
    void setUp() {
        authTokenIssueService = new AuthTokenIssueService(
                accessTokenProvider,
                new AccessTokenProperties(
                        "ssing",
                        "a".repeat(32),
                        ACCESS_TOKEN_EXPIRATION
                ),
                refreshTokenService
        );
    }

    @Test
    void issueAccessToken은_Access_Token만_발급하고_RefreshTokenService를_호출하지_않는다() {
        Member member = activeConsumer();
        when(accessTokenProvider.createAccessToken(MEMBER_ID, MemberRole.CONSUMER))
                .thenReturn(ACCESS_TOKEN);

        IssuedAccessToken result = authTokenIssueService.issueAccessToken(member);

        assertThat(result).isEqualTo(IssuedAccessToken.of(
                ACCESS_TOKEN,
                "Bearer",
                ACCESS_TOKEN_EXPIRATION.toSeconds()
        ));
        verify(accessTokenProvider).createAccessToken(MEMBER_ID, MemberRole.CONSUMER);
        verifyNoInteractions(refreshTokenService);
    }

    private Member activeConsumer() {
        Member member = Member.create(
                "테스트회원",
                null,
                MemberRole.CONSUMER,
                MemberStatus.ACTIVE
        );
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        return member;
    }
}
