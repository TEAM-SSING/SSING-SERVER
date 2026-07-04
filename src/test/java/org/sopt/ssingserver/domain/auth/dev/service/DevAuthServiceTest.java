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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sopt.ssingserver.domain.auth.dev.dto.response.DevAuthTokenResponse;
import org.sopt.ssingserver.domain.auth.dev.dto.response.DevPersonaListResponse;
import org.sopt.ssingserver.domain.auth.dev.dto.response.DevPersonaResponse;
import org.sopt.ssingserver.domain.auth.dev.entity.DevPersona;
import org.sopt.ssingserver.domain.auth.dev.enums.DevPersonaTemplate;
import org.sopt.ssingserver.domain.auth.dev.error.DevAuthErrorCode;
import org.sopt.ssingserver.domain.auth.dev.repository.DevPersonaRepository;
import org.sopt.ssingserver.domain.auth.dto.response.InstructorStatusResponse;
import org.sopt.ssingserver.domain.auth.dto.result.IssuedAuthTokens;
import org.sopt.ssingserver.domain.auth.service.AuthTokenIssueService;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.instructor.repository.InstructorProfileRepository;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.Gender;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.member.repository.MemberRepository;
import org.sopt.ssingserver.global.error.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

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
    private AuthTokenIssueService authTokenIssueService;

    @Test
    void getPersonas는_목록_조회에서_강사상태를_memberId_목록으로_한번에_조회한다() {
        DevAuthService service = createService();
        Member consumer = createMemberWithId(1L, "일반소비자", MemberRole.CONSUMER, MemberStatus.ACTIVE);
        Member instructor = createMemberWithId(2L, "승인강사", MemberRole.INSTRUCTOR, MemberStatus.ACTIVE);
        Instant consumerCreatedAt = Instant.parse("2026-07-01T01:00:00Z");
        Instant instructorCreatedAt = Instant.parse("2026-07-02T02:00:00Z");
        DevPersona consumerPersona = DevPersona.create(
                "general-consumer",
                consumer,
                DevPersonaTemplate.GENERAL_CONSUMER
        );
        DevPersona instructorPersona = DevPersona.create(
                "approved-instructor",
                instructor,
                DevPersonaTemplate.INSTRUCTOR_APPROVED
        );
        ReflectionTestUtils.setField(consumerPersona, "createdAt", consumerCreatedAt);
        ReflectionTestUtils.setField(instructorPersona, "createdAt", instructorCreatedAt);
        InstructorProfile approvedProfile = InstructorProfile.create(
                instructor,
                "승인강사",
                "010-0000-0000",
                Gender.MALE,
                LocalDate.of(2000, 1, 1),
                "테스트 강사 프로필",
                LocalDate.of(2020, 1, 1),
                InstructorApprovalStatus.APPROVED,
                FIXED_CLOCK.instant()
        );

        when(devPersonaRepository.findAllByOrderByCreatedAtAsc())
                .thenReturn(List.of(consumerPersona, instructorPersona));
        when(instructorProfileRepository.findAllByMemberIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(approvedProfile));

        DevPersonaListResponse response = service.getPersonas();

        assertThat(response.personas())
                .extracting(DevPersonaResponse::personaKey)
                .containsExactly("general-consumer", "approved-instructor");
        assertThat(response.personas())
                .extracting(DevPersonaResponse::instructorStatus)
                .containsExactly(InstructorStatusResponse.NONE, InstructorStatusResponse.APPROVED);
        assertThat(response.personas())
                .extracting(DevPersonaResponse::createdAt)
                .containsExactly(consumerCreatedAt, instructorCreatedAt);
        // 목록 API는 화면에서 자주 호출되므로 persona 수만큼 강사 프로필을 조회하지 않는다.
        verify(instructorProfileRepository).findAllByMemberIdIn(List.of(1L, 2L));
        verify(instructorProfileRepository, never()).findByMemberId(any());
    }

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
        verifyNoInteractions(authTokenIssueService);
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
        verifyNoInteractions(authTokenIssueService);
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
        verifyNoInteractions(authTokenIssueService);
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
        verifyNoInteractions(authTokenIssueService);
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
        verifyNoInteractions(authTokenIssueService);
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
        IssuedAuthTokens issuedTokens = IssuedAuthTokens.of(
                "access-token",
                "refresh-token",
                "Bearer",
                3600
        );

        when(devPersonaRepository.findByPersonaKey("suspended-consumer")).thenReturn(Optional.of(devPersona));
        when(authTokenIssueService.issueTokens(member)).thenReturn(issuedTokens);
        when(instructorProfileRepository.findByMemberId(isNull())).thenReturn(Optional.empty());

        DevAuthTokenResponse response = service.issueToken("suspended-consumer");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.persona().personaKey()).isEqualTo("suspended-consumer");
        assertThat(response.persona().memberStatus()).isEqualTo(MemberStatus.SUSPENDED);
        verify(authTokenIssueService).issueTokens(member);
    }

    private DevAuthService createService() {
        return new DevAuthService(
                devPersonaRepository,
                memberRepository,
                instructorProfileRepository,
                authTokenIssueService,
                FIXED_CLOCK
        );
    }

    private Member createMemberWithId(
            Long id,
            String nickname,
            MemberRole role,
            MemberStatus status
    ) {
        Member member = Member.create(
                nickname,
                null,
                role,
                status
        );
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
