package org.sopt.ssingserver.domain.auth.dev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sopt.ssingserver.domain.auth.dev.dto.response.DevAuthTokenResponse;
import org.sopt.ssingserver.domain.auth.dev.entity.DevPersona;
import org.sopt.ssingserver.domain.auth.dev.enums.DevPersonaTemplate;
import org.sopt.ssingserver.domain.auth.dev.error.DevAuthErrorCode;
import org.sopt.ssingserver.domain.auth.dev.repository.DevPersonaRepository;
import org.sopt.ssingserver.domain.auth.service.AuthTokenIssuer;
import org.sopt.ssingserver.domain.auth.service.IssuedAuthTokens;
import org.sopt.ssingserver.domain.instructor.repository.InstructorProfileRepository;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.member.repository.MemberRepository;
import org.sopt.ssingserver.global.error.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class DevAuthServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-04T00:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private DevPersonaRepository devPersonaRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private InstructorProfileRepository instructorProfileRepository;

    @Mock
    private AuthTokenIssuer authTokenIssuer;

    @Test
    void createPersona는_이미_있는_personaKey면_회원을_만들지_않고_중복_오류를_던진다() {
        DevAuthService service = createService();
        when(devPersonaRepository.existsByPersonaKey("android-consumer")).thenReturn(true);

        assertThatThrownBy(() -> service.createPersona(
                "android-consumer",
                "안드소비자",
                "GENERAL_CONSUMER"
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isSameAs(DevAuthErrorCode.DEV_PERSONA_ALREADY_EXISTS));

        // 중복 persona는 DB 계정을 새로 만들 필요가 없으므로 member 저장까지 진행하지 않는다.
        verifyNoInteractions(memberRepository);
        verifyNoInteractions(authTokenIssuer);
    }

    @Test
    void createPersona는_지원하지_않는_template이면_회원과_persona를_저장하지_않는다() {
        DevAuthService service = createService();

        assertThatThrownBy(() -> service.createPersona(
                "admin-user",
                "관리자",
                "ADMIN"
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isSameAs(DevAuthErrorCode.DEV_PERSONA_INVALID_TEMPLATE));

        // ADMIN 템플릿은 이번 개발 도구 범위에서 제외했으므로 저장 로직으로 넘어가면 안 된다.
        verifyNoInteractions(memberRepository);
        verify(devPersonaRepository, never()).saveAndFlush(any(DevPersona.class));
        verifyNoInteractions(authTokenIssuer);
    }

    @Test
    void createPersona는_template이_null이어도_NPE가_아니라_INVALID_TEMPLATE_오류를_던진다() {
        DevAuthService service = createService();

        assertThatThrownBy(() -> service.createPersona(
                "null-template-user",
                "템플릿없음",
                null
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isSameAs(DevAuthErrorCode.DEV_PERSONA_INVALID_TEMPLATE));

        // template이 유효하지 않으면 회원 생성이나 persona 저장까지 진행하지 않는다.
        verifyNoInteractions(memberRepository);
        verifyNoInteractions(devPersonaRepository);
        verifyNoInteractions(authTokenIssuer);
    }

    @Test
    void createPersona는_동시에_같은_personaKey가_저장되면_중복_오류로_변환한다() {
        DevAuthService service = createService();
        Member member = Member.create(
                "동시생성",
                null,
                MemberRole.CONSUMER,
                MemberStatus.ACTIVE
        );

        when(devPersonaRepository.existsByPersonaKey("race-user")).thenReturn(false);
        when(memberRepository.save(any(Member.class))).thenReturn(member);
        when(devPersonaRepository.saveAndFlush(any(DevPersona.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate personaKey"));

        assertThatThrownBy(() -> service.createPersona(
                "race-user",
                "동시생성",
                "GENERAL_CONSUMER"
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isSameAs(DevAuthErrorCode.DEV_PERSONA_ALREADY_EXISTS));

        // existsByPersonaKey 통과 이후 DB unique constraint에서 충돌해도 API 에러 코드는 동일해야 한다.
        verifyNoInteractions(authTokenIssuer);
    }

    @Test
    void issueToken은_없는_personaKey면_토큰을_발급하지_않고_NOT_FOUND_오류를_던진다() {
        DevAuthService service = createService();
        when(devPersonaRepository.findByPersonaKey("missing-user")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issueToken("missing-user"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isSameAs(DevAuthErrorCode.DEV_PERSONA_NOT_FOUND));

        // 토큰 API는 기존 persona만 대상으로 하므로, 못 찾으면 토큰 발급기로 넘기지 않는다.
        verifyNoInteractions(authTokenIssuer);
    }

    @Test
    void issueToken은_SUSPENDED_CONSUMER도_개발용_토큰을_발급할_수_있다() {
        DevAuthService service = createService();
        Member member = Member.create(
                "정지소비자",
                null,
                MemberRole.CONSUMER,
                MemberStatus.SUSPENDED
        );
        DevPersona devPersona = DevPersona.create(
                "suspended-consumer",
                member,
                DevPersonaTemplate.SUSPENDED_CONSUMER
        );
        IssuedAuthTokens issuedTokens = new IssuedAuthTokens(
                "access-token",
                "refresh-token",
                "Bearer",
                3600
        );

        when(devPersonaRepository.findByPersonaKey("suspended-consumer")).thenReturn(Optional.of(devPersona));
        when(authTokenIssuer.issueTokens(member)).thenReturn(issuedTokens);
        when(instructorProfileRepository.findByMemberId(isNull())).thenReturn(Optional.empty());

        DevAuthTokenResponse response = service.issueToken("suspended-consumer");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.persona().personaKey()).isEqualTo("suspended-consumer");
        assertThat(response.persona().memberStatus()).isEqualTo(MemberStatus.SUSPENDED);
        verify(authTokenIssuer).issueTokens(member);
    }

    private DevAuthService createService() {
        return new DevAuthService(
                devPersonaRepository,
                memberRepository,
                instructorProfileRepository,
                authTokenIssuer,
                FIXED_CLOCK
        );
    }
}
