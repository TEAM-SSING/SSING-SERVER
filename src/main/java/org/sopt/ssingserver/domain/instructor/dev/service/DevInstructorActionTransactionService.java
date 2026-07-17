package org.sopt.ssingserver.domain.instructor.dev.service;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.auth.enums.OAuthProvider;
import org.sopt.ssingserver.domain.auth.repository.OAuthAccountRepository;
import org.sopt.ssingserver.domain.instructor.dev.dto.request.DevInstructorConfigurationRequest;
import org.sopt.ssingserver.domain.instructor.dev.dto.request.ExecuteDevInstructorActionRequest;
import org.sopt.ssingserver.domain.instructor.dev.dto.response.DevInstructorActionExecutionResponse;
import org.sopt.ssingserver.domain.instructor.dev.dto.response.DevInstructorConfigurationResponse;
import org.sopt.ssingserver.domain.instructor.dev.dto.response.DevInstructorMemberResponse;
import org.sopt.ssingserver.domain.instructor.dev.enums.DevInstructorActionKey;
import org.sopt.ssingserver.domain.instructor.dev.error.DevInstructorErrorCode;
import org.sopt.ssingserver.domain.instructor.dto.request.InstructorMatchingExposureRequest;
import org.sopt.ssingserver.domain.instructor.entity.InstructorMatchingSetting;
import org.sopt.ssingserver.domain.instructor.entity.InstructorPricePolicy;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.instructor.repository.InstructorMatchingSettingRepository;
import org.sopt.ssingserver.domain.instructor.repository.InstructorPricePolicyRepository;
import org.sopt.ssingserver.domain.instructor.repository.InstructorProfileRepository;
import org.sopt.ssingserver.domain.instructor.service.InstructorService;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.member.repository.MemberRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestRepository;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.sopt.ssingserver.domain.resort.repository.ResortRepository;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.BusinessValidationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile({"local", "dev"})
@ConditionalOnProperty(name = "ssing.dev-instructor-actions.enabled", havingValue = "true")
@Service
@RequiredArgsConstructor
public class DevInstructorActionTransactionService {

    private final MemberRepository memberRepository;
    private final MatchingRequestRepository matchingRequestRepository;
    private final OAuthAccountRepository oauthAccountRepository;
    private final InstructorProfileRepository instructorProfileRepository;
    private final InstructorMatchingSettingRepository instructorMatchingSettingRepository;
    private final InstructorPricePolicyRepository instructorPricePolicyRepository;
    private final ResortRepository resortRepository;
    private final DevInstructorQueryService queryService;
    private final DevInstructorConfigurationPolicy configurationPolicy;
    private final InstructorService instructorService;
    private final EntityManager entityManager;
    private final Clock clock;

    @Transactional
    public DevInstructorActionExecutionResponse execute(
            Long memberId,
            ExecuteDevInstructorActionRequest request
    ) {
        // 화면에 보여준 상태 확인부터 관련 row 변경까지 한 트랜잭션으로 묶어 반쪽 저장을 막는다.
        LockedState lockedState = lockState(memberId);
        DevInstructorMemberResponse before = queryService.getMember(memberId);
        validateLatestState(request, before);
        validatePayloadShape(request);

        executeMutation(request, lockedState, before);
        entityManager.flush();
        // 커밋 뒤에도 같은 stateToken이 나오도록 DB에 저장된 시간 정밀도로 다시 읽는다.
        entityManager.clear();

        DevInstructorMemberResponse after = queryService.getMember(memberId);
        return new DevInstructorActionExecutionResponse(request.actionKey(), before, after);
    }

    private LockedState lockState(Long memberId) {
        // 모든 개발 동작이 Member -> Profile -> Setting -> Price 순서로 잠가 서로 다른 버튼의 교착을 줄인다.
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(
                        DevInstructorErrorCode.DEV_INSTRUCTOR_MEMBER_NOT_FOUND
                ));
        oauthAccountRepository.findByMemberIdAndProvider(memberId, OAuthProvider.KAKAO)
                .orElseThrow(() -> new BusinessException(
                        DevInstructorErrorCode.DEV_INSTRUCTOR_MEMBER_NOT_FOUND
                ));
        Optional<InstructorProfile> profile = instructorProfileRepository.findByMemberIdForUpdate(memberId);
        Optional<InstructorMatchingSetting> setting = profile.flatMap(value ->
                instructorMatchingSettingRepository.findByInstructorProfileIdForUpdate(value.getId())
        );
        List<InstructorPricePolicy> activePricePolicies = profile
                .map(value -> instructorPricePolicyRepository.findActiveByInstructorProfileIdForUpdate(
                        value.getId()
                ))
                .orElseGet(List::of);
        return new LockedState(member, profile, setting, activePricePolicies);
    }

    private void validateLatestState(
            ExecuteDevInstructorActionRequest request,
            DevInstructorMemberResponse before
    ) {
        // 목록을 본 뒤 다른 요청이 먼저 상태를 바꿨다면 오래된 버튼 실행을 거절한다.
        if (!Objects.equals(request.stateToken(), before.stateToken())) {
            throw new BusinessException(DevInstructorErrorCode.DEV_INSTRUCTOR_STATE_CHANGED);
        }
        if (request.actionKey() == null || !before.availableActions().contains(request.actionKey())) {
            throw new BusinessException(DevInstructorErrorCode.DEV_INSTRUCTOR_ACTION_NOT_AVAILABLE);
        }
    }

    private void validatePayloadShape(ExecuteDevInstructorActionRequest request) {
        boolean configurationRequired = request.actionKey() == DevInstructorActionKey.APPROVE_WITH_CONFIGURATION
                || request.actionKey() == DevInstructorActionKey.SAVE_CONFIGURATION;
        if (configurationRequired) {
            configurationPolicy.validate(request.configuration());
            return;
        }
        if (request.configuration() != null) {
            throw BusinessValidationException.of(
                    "configuration",
                    "이 동작에는 강사 설정값을 함께 보낼 수 없습니다."
            );
        }
    }

    private void executeMutation(
            ExecuteDevInstructorActionRequest request,
            LockedState lockedState,
            DevInstructorMemberResponse before
    ) {
        switch (request.actionKey()) {
            case CREATE_APPLICATION -> createApplication(lockedState);
            case APPROVE_WITH_CONFIGURATION -> approveWithConfiguration(
                    lockedState,
                    request.configuration()
            );
            case SAVE_CONFIGURATION -> saveConfiguration(lockedState, request.configuration());
            case START_MATCHING -> startMatching(lockedState, before.configuration());
            case STOP_MATCHING -> stopMatching(lockedState);
        }
    }

    private void createApplication(LockedState lockedState) {
        Member member = lockedState.member();
        if (lockedState.profile().isPresent()
                || member.getStatus() != MemberStatus.ACTIVE
                || member.getRole() != MemberRole.CONSUMER) {
            throw new BusinessException(DevInstructorErrorCode.DEV_INSTRUCTOR_ACTION_NOT_AVAILABLE);
        }
        InstructorProfile profile = InstructorProfile.create(
                member,
                member.getNickname(),
                DevInstructorApplicationDefaults.PHONE,
                DevInstructorApplicationDefaults.GENDER,
                DevInstructorApplicationDefaults.BIRTH_DATE,
                DevInstructorApplicationDefaults.INTRO,
                DevInstructorApplicationDefaults.CAREER_START_DATE,
                InstructorApprovalStatus.PENDING,
                null
        );
        instructorProfileRepository.save(profile);
    }

    private void approveWithConfiguration(
            LockedState lockedState,
            DevInstructorConfigurationRequest configuration
    ) {
        Member member = lockedState.member();
        InstructorProfile profile = requiredProfile(lockedState);
        if (member.getStatus() != MemberStatus.ACTIVE
                || member.getRole() != MemberRole.CONSUMER
                || profile.getApprovalStatus() != InstructorApprovalStatus.PENDING
                || matchingRequestRepository.existsByMemberIdAndStatusIn(
                        member.getId(),
                        DevInstructorActionPolicy.roleChangeBlockingStatuses()
                )) {
            throw new BusinessException(DevInstructorErrorCode.DEV_INSTRUCTOR_ACTION_NOT_AVAILABLE);
        }
        Resort resort = findResort(configuration.resortCode());
        // 승인·역할 승격·설정·가격 중 하나라도 실패하면 execute 트랜잭션이 전부 되돌린다.
        profile.approve(resort, clock.instant());
        member.promoteToInstructor();
        storeConfiguration(lockedState, profile, configuration);
    }

    private void saveConfiguration(
            LockedState lockedState,
            DevInstructorConfigurationRequest configuration
    ) {
        Member member = lockedState.member();
        InstructorProfile profile = requiredProfile(lockedState);
        if (member.getStatus() != MemberStatus.ACTIVE
                || member.getRole() != MemberRole.INSTRUCTOR
                || profile.getApprovalStatus() != InstructorApprovalStatus.APPROVED
                || lockedState.setting().map(InstructorMatchingSetting::isExposed).orElse(false)) {
            throw new BusinessException(DevInstructorErrorCode.DEV_INSTRUCTOR_ACTION_NOT_AVAILABLE);
        }
        profile.changeResort(findResort(configuration.resortCode()));
        storeConfiguration(lockedState, profile, configuration);
    }

    private void storeConfiguration(
            LockedState lockedState,
            InstructorProfile profile,
            DevInstructorConfigurationRequest configuration
    ) {
        profile.registerCertificate(configurationPolicy.testCertificateFor(configuration.sport()));
        InstructorMatchingSetting setting = lockedState.setting()
                .map(existing -> updateDraft(existing, configuration))
                .orElseGet(() -> createDraft(profile, configuration));
        instructorMatchingSettingRepository.save(setting);

        lockedState.activePricePolicies().forEach(InstructorPricePolicy::deactivate);
        instructorPricePolicyRepository.save(InstructorPricePolicy.createActive(
                profile,
                configuration.basePriceAmount(),
                configuration.additionalPersonPriceAmount()
        ));
    }

    private InstructorMatchingSetting createDraft(
            InstructorProfile profile,
            DevInstructorConfigurationRequest configuration
    ) {
        return InstructorMatchingSetting.createDraft(
                profile,
                configuration.sport(),
                configuration.lessonLevels(),
                configuration.availableDurationMinutes(),
                configuration.maxHeadcount(),
                true
        );
    }

    private InstructorMatchingSetting updateDraft(
            InstructorMatchingSetting setting,
            DevInstructorConfigurationRequest configuration
    ) {
        setting.updateDraftConditions(
                configuration.sport(),
                configuration.lessonLevels(),
                configuration.availableDurationMinutes(),
                configuration.maxHeadcount(),
                true
        );
        return setting;
    }

    private void startMatching(
            LockedState lockedState,
            DevInstructorConfigurationResponse configuration
    ) {
        InstructorProfile profile = requiredProfile(lockedState);
        if (lockedState.member().getStatus() != MemberStatus.ACTIVE
                || lockedState.member().getRole() != MemberRole.INSTRUCTOR
                || profile.getApprovalStatus() != InstructorApprovalStatus.APPROVED
                || lockedState.setting().isEmpty()
                || lockedState.setting().orElseThrow().isExposed()
                || !configuration.complete()) {
            throw new BusinessException(DevInstructorErrorCode.DEV_INSTRUCTOR_ACTION_NOT_AVAILABLE);
        }
        instructorService.startExposure(
                lockedState.member().getId(),
                new InstructorMatchingExposureRequest(
                        Objects.requireNonNull(configuration.sport()),
                        configuration.lessonLevels(),
                        configuration.availableDurationMinutes(),
                        Objects.requireNonNull(configuration.maxHeadcount()),
                        true
                )
        );
    }

    private void stopMatching(LockedState lockedState) {
        InstructorProfile profile = requiredProfile(lockedState);
        if (lockedState.member().getStatus() != MemberStatus.ACTIVE
                || lockedState.member().getRole() != MemberRole.INSTRUCTOR
                || profile.getApprovalStatus() != InstructorApprovalStatus.APPROVED
                || lockedState.setting().isEmpty()
                || !lockedState.setting().orElseThrow().isExposed()) {
            throw new BusinessException(DevInstructorErrorCode.DEV_INSTRUCTOR_ACTION_NOT_AVAILABLE);
        }
        instructorService.cancelExposure(lockedState.member().getId());
    }

    private InstructorProfile requiredProfile(LockedState lockedState) {
        return lockedState.profile()
                .orElseThrow(() -> new BusinessException(
                        DevInstructorErrorCode.DEV_INSTRUCTOR_ACTION_NOT_AVAILABLE
                ));
    }

    private Resort findResort(String resortCode) {
        return resortRepository.findByCode(resortCode)
                .orElseThrow(() -> BusinessValidationException.of(
                        "resortCode",
                        "DB에 존재하는 리조트를 선택해 주세요."
                ));
    }

    private record LockedState(
            Member member,
            Optional<InstructorProfile> profile,
            Optional<InstructorMatchingSetting> setting,
            List<InstructorPricePolicy> activePricePolicies
    ) {
    }
}
