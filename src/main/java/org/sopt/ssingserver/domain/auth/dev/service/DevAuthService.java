package org.sopt.ssingserver.domain.auth.dev.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.sopt.ssingserver.domain.auth.dev.dto.response.CreateDevPersonaResponse;
import org.sopt.ssingserver.domain.auth.dev.dto.response.DevAuthTokenResponse;
import org.sopt.ssingserver.domain.auth.dev.dto.response.DevMetaResponse;
import org.sopt.ssingserver.domain.auth.dev.dto.response.DevPersonaListResponse;
import org.sopt.ssingserver.domain.auth.dev.dto.response.DevPersonaResponse;
import org.sopt.ssingserver.domain.auth.dev.dto.response.DevPersonaSnapshotResponse;
import org.sopt.ssingserver.domain.auth.dev.entity.DevPersona;
import org.sopt.ssingserver.domain.auth.dev.enums.DevPersonaTemplate;
import org.sopt.ssingserver.domain.auth.dev.error.DevAuthErrorCode;
import org.sopt.ssingserver.domain.auth.dev.repository.DevPersonaRepository;
import org.sopt.ssingserver.domain.auth.dto.response.InstructorStatusResponse;
import org.sopt.ssingserver.domain.auth.service.AuthTokenIssuer;
import org.sopt.ssingserver.domain.auth.service.IssuedAuthTokens;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.instructor.repository.InstructorProfileRepository;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.Gender;
import org.sopt.ssingserver.domain.member.repository.MemberRepository;
import org.sopt.ssingserver.global.error.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DevAuthService {

    private static final String DEV_PROFILE_PHONE = "010-0000-0000";
    private static final LocalDate DEV_PROFILE_BIRTH_DATE = LocalDate.of(2000, 1, 1);
    private static final LocalDate DEV_PROFILE_CAREER_START_DATE = LocalDate.of(2020, 1, 1);
    private static final String DEV_PROFILE_INTRO = "개발용 강사 프로필입니다.";

    private final DevPersonaRepository devPersonaRepository;
    private final MemberRepository memberRepository;
    private final InstructorProfileRepository instructorProfileRepository;
    private final AuthTokenIssuer authTokenIssuer;

    public DevAuthService(
            DevPersonaRepository devPersonaRepository,
            MemberRepository memberRepository,
            InstructorProfileRepository instructorProfileRepository,
            AuthTokenIssuer authTokenIssuer
    ) {
        this.devPersonaRepository = devPersonaRepository;
        this.memberRepository = memberRepository;
        this.instructorProfileRepository = instructorProfileRepository;
        this.authTokenIssuer = authTokenIssuer;
    }

    public DevPersonaListResponse getPersonas() {
        List<DevPersonaResponse> personas = devPersonaRepository.findAll()
                .stream()
                .map(this::toPersonaResponse)
                .toList();
        return new DevPersonaListResponse(personas);
    }

    @Transactional
    public CreateDevPersonaResponse createPersona(
            String personaKey,
            String nickname,
            String templateValue
    ) {
        String normalizedPersonaKey = personaKey.trim();
        String normalizedNickname = nickname.trim();
        DevPersonaTemplate template = DevPersonaTemplate.from(templateValue.trim());
        validatePersonaKeyNotExists(normalizedPersonaKey);

        try {
            Member member = memberRepository.save(Member.create(
                    normalizedNickname,
                    null,
                    template.memberRole(),
                    template.memberStatus()
            ));
            createInstructorProfileIfNeeded(member, normalizedNickname, template);
            DevPersona devPersona = devPersonaRepository.save(DevPersona.create(
                    normalizedPersonaKey,
                    member,
                    template
            ));
            return new CreateDevPersonaResponse(toPersonaResponse(devPersona));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(DevAuthErrorCode.DEV_PERSONA_ALREADY_EXISTS, exception);
        }
    }

    @Transactional
    public DevAuthTokenResponse issueToken(String personaKey) {
        DevPersona devPersona = devPersonaRepository.findByPersonaKey(personaKey.trim())
                .orElseThrow(() -> new BusinessException(DevAuthErrorCode.DEV_PERSONA_NOT_FOUND));
        IssuedAuthTokens tokens = authTokenIssuer.issueTokens(devPersona.getMember());
        DevPersonaResponse persona = toPersonaResponse(devPersona);
        return new DevAuthTokenResponse(
                tokens.accessToken(),
                tokens.refreshToken(),
                tokens.tokenType(),
                tokens.expiresIn(),
                DevPersonaSnapshotResponse.from(persona),
                persona.devMeta()
        );
    }

    private void validatePersonaKeyNotExists(String personaKey) {
        if (devPersonaRepository.existsByPersonaKey(personaKey)) {
            throw new BusinessException(DevAuthErrorCode.DEV_PERSONA_ALREADY_EXISTS);
        }
    }

    private void createInstructorProfileIfNeeded(
            Member member,
            String nickname,
            DevPersonaTemplate template
    ) {
        template.instructorProfilePlan()
                .approvalStatus()
                .ifPresent(approvalStatus -> instructorProfileRepository.save(createDevInstructorProfile(
                        member,
                        nickname,
                        approvalStatus
                )));
    }

    private InstructorProfile createDevInstructorProfile(
            Member member,
            String nickname,
            InstructorApprovalStatus approvalStatus
    ) {
        return InstructorProfile.create(
                member,
                nickname,
                DEV_PROFILE_PHONE,
                Gender.MALE,
                DEV_PROFILE_BIRTH_DATE,
                DEV_PROFILE_INTRO,
                DEV_PROFILE_CAREER_START_DATE,
                approvalStatus,
                resolveApprovedAt(approvalStatus)
        );
    }

    private Instant resolveApprovedAt(InstructorApprovalStatus approvalStatus) {
        if (approvalStatus == InstructorApprovalStatus.APPROVED) {
            return Instant.now();
        }
        return null;
    }

    private DevPersonaResponse toPersonaResponse(DevPersona devPersona) {
        Member member = devPersona.getMember();
        DevPersonaTemplate template = devPersona.getTemplate();
        return new DevPersonaResponse(
                devPersona.getPersonaKey(),
                member.getNickname(),
                template,
                member.getRole(),
                member.getStatus(),
                resolveInstructorStatus(member.getId()),
                DevMetaResponse.manual(template)
        );
    }

    private InstructorStatusResponse resolveInstructorStatus(Long memberId) {
        return instructorProfileRepository.findByMemberId(memberId)
                .map(InstructorProfile::getApprovalStatus)
                .map(InstructorStatusResponse::from)
                .orElse(InstructorStatusResponse.NONE);
    }
}
