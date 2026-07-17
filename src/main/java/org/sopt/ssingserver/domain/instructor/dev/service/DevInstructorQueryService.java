package org.sopt.ssingserver.domain.instructor.dev.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.auth.entity.OAuthAccount;
import org.sopt.ssingserver.domain.auth.enums.OAuthProvider;
import org.sopt.ssingserver.domain.auth.repository.OAuthAccountRepository;
import org.sopt.ssingserver.domain.instructor.dev.dto.response.DevInstructorConfigurationResponse;
import org.sopt.ssingserver.domain.instructor.dev.dto.response.DevInstructorMemberListResponse;
import org.sopt.ssingserver.domain.instructor.dev.dto.response.DevInstructorMemberResponse;
import org.sopt.ssingserver.domain.instructor.dev.dto.response.DevInstructorResortOptionResponse;
import org.sopt.ssingserver.domain.instructor.dev.enums.DevInstructorActionKey;
import org.sopt.ssingserver.domain.instructor.dev.error.DevInstructorErrorCode;
import org.sopt.ssingserver.domain.instructor.entity.InstructorMatchingSetting;
import org.sopt.ssingserver.domain.instructor.entity.InstructorPricePolicy;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.instructor.enums.InstructorCertificateType;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.repository.InstructorMatchingSettingRepository;
import org.sopt.ssingserver.domain.instructor.repository.InstructorPricePolicyRepository;
import org.sopt.ssingserver.domain.instructor.repository.InstructorProfileRepository;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestRepository;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.sopt.ssingserver.domain.resort.repository.ResortRepository;
import org.sopt.ssingserver.global.error.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile({"local", "dev"})
@ConditionalOnProperty(name = "ssing.dev-instructor-actions.enabled", havingValue = "true")
@Service
@RequiredArgsConstructor
public class DevInstructorQueryService {

    private final OAuthAccountRepository oauthAccountRepository;
    private final InstructorProfileRepository instructorProfileRepository;
    private final InstructorMatchingSettingRepository instructorMatchingSettingRepository;
    private final InstructorPricePolicyRepository instructorPricePolicyRepository;
    private final ResortRepository resortRepository;
    private final MatchingRequestRepository matchingRequestRepository;
    private final DevInstructorActionPolicy actionPolicy;
    private final DevInstructorConfigurationPolicy configurationPolicy;
    private final DevInstructorStateTokenFactory stateTokenFactory;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DevInstructorMemberListResponse getMembers(int page, int size) {
        Page<OAuthAccount> accountPage = oauthAccountRepository.findAllByProviderOrderByIdDesc(
                OAuthProvider.KAKAO,
                PageRequest.of(page, size)
        );
        RelatedState relatedState = loadRelatedState(accountPage.getContent());
        List<DevInstructorMemberResponse> members = accountPage.getContent().stream()
                .map(account -> toResponse(account, relatedState))
                .toList();
        List<DevInstructorResortOptionResponse> resorts = resortRepository.findAllByOrderByDisplayNameAsc()
                .stream()
                .map(this::toResortOption)
                .toList();

        return new DevInstructorMemberListResponse(
                clock.instant(),
                accountPage.getNumber(),
                accountPage.getSize(),
                accountPage.getTotalElements(),
                accountPage.getTotalPages(),
                accountPage.hasPrevious(),
                accountPage.hasNext(),
                resorts,
                DevInstructorApplicationDefaults.response(),
                members
        );
    }

    @Transactional(readOnly = true)
    public DevInstructorMemberResponse getMember(Long memberId) {
        OAuthAccount oauthAccount = oauthAccountRepository
                .findByMemberIdAndProvider(memberId, OAuthProvider.KAKAO)
                .orElseThrow(() -> new BusinessException(
                        DevInstructorErrorCode.DEV_INSTRUCTOR_MEMBER_NOT_FOUND
                ));
        RelatedState relatedState = loadRelatedState(List.of(oauthAccount));
        return toResponse(oauthAccount, relatedState);
    }

    private RelatedState loadRelatedState(List<OAuthAccount> oauthAccounts) {
        if (oauthAccounts.isEmpty()) {
            return RelatedState.empty();
        }
        List<Long> memberIds = oauthAccounts.stream()
                .map(OAuthAccount::getMember)
                .map(Member::getId)
                .toList();
        Set<Long> activeConsumerFlowMemberIds = matchingRequestRepository.findMemberIdsByStatusIn(
                memberIds,
                DevInstructorActionPolicy.roleChangeBlockingStatuses()
        );
        // 페이지의 연관 상태를 종류별 묶음 조회해 회원 수만큼 쿼리가 반복되는 N+1을 피한다.
        Map<Long, InstructorProfile> profileByMemberId = instructorProfileRepository
                .findAllByMemberIdIn(memberIds)
                .stream()
                .collect(Collectors.toMap(
                        profile -> profile.getMember().getId(),
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
        List<Long> profileIds = profileByMemberId.values().stream()
                .map(InstructorProfile::getId)
                .toList();
        if (profileIds.isEmpty()) {
            return new RelatedState(profileByMemberId, Map.of(), Map.of(), activeConsumerFlowMemberIds);
        }
        Map<Long, InstructorMatchingSetting> settingByProfileId = instructorMatchingSettingRepository
                .findAllByInstructorProfileIdIn(profileIds)
                .stream()
                .collect(Collectors.toMap(
                        setting -> setting.getInstructorProfile().getId(),
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
        Map<Long, List<InstructorPricePolicy>> activePricesByProfileId = instructorPricePolicyRepository
                .findAllByInstructorProfileIdInAndIsActiveTrueOrderByInstructorProfileIdAscIdDesc(profileIds)
                .stream()
                .collect(Collectors.groupingBy(
                        pricePolicy -> pricePolicy.getInstructorProfile().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        return new RelatedState(
                profileByMemberId,
                settingByProfileId,
                activePricesByProfileId,
                activeConsumerFlowMemberIds
        );
    }

    private DevInstructorMemberResponse toResponse(OAuthAccount oauthAccount, RelatedState relatedState) {
        Member member = oauthAccount.getMember();
        InstructorProfile profile = relatedState.profileByMemberId().get(member.getId());
        InstructorMatchingSetting setting = profile == null
                ? null
                : relatedState.settingByProfileId().get(profile.getId());
        List<InstructorPricePolicy> activePricePolicies = profile == null
                ? List.of()
                : relatedState.activePricesByProfileId().getOrDefault(profile.getId(), List.of());
        boolean configurationComplete = configurationPolicy.isConfigurationComplete(
                profile,
                setting,
                activePricePolicies
        );
        DevInstructorActionContext context = new DevInstructorActionContext(
                member.getRole(),
                member.getStatus(),
                profile != null,
                profile == null ? null : profile.getApprovalStatus(),
                configurationComplete,
                setting != null && setting.isExposed(),
                relatedState.activeConsumerFlowMemberIds().contains(member.getId())
        );
        List<DevInstructorActionKey> actions = actionPolicy.availableActions(context);
        String stateToken = stateTokenFactory.create(
                oauthAccount,
                member,
                profile,
                setting,
                activePricePolicies,
                actions
        );

        return new DevInstructorMemberResponse(
                oauthAccount.getId(),
                oauthAccount.getProvider(),
                member.getId(),
                member.getNickname(),
                member.getRole(),
                member.getStatus(),
                member.getCreatedAt(),
                profile == null ? null : profile.getId(),
                profile == null ? null : profile.getApprovalStatus(),
                profile == null ? null : profile.getApprovedAt(),
                certificates(profile),
                configuration(profile, setting, activePricePolicies, configurationComplete),
                actions,
                stateToken,
                diagnostics(
                        member,
                        profile,
                        setting,
                        activePricePolicies,
                        configurationComplete,
                        relatedState.activeConsumerFlowMemberIds().contains(member.getId())
                )
        );
    }

    private DevInstructorConfigurationResponse configuration(
            InstructorProfile profile,
            InstructorMatchingSetting setting,
            List<InstructorPricePolicy> activePricePolicies,
            boolean complete
    ) {
        Resort resort = profile == null ? null : profile.getResort();
        InstructorPricePolicy selectedPricePolicy = activePricePolicies.isEmpty()
                ? null
                : activePricePolicies.getFirst();
        return new DevInstructorConfigurationResponse(
                setting == null ? null : setting.getId(),
                resort == null ? null : resort.getId(),
                resort == null ? null : resort.getCode(),
                resort == null ? null : resort.getDisplayName(),
                setting == null ? null : setting.getSport(),
                lessonLevels(setting),
                durations(setting),
                setting == null ? null : setting.getMaxHeadcount(),
                setting != null && setting.isEquipmentReady(),
                setting != null && setting.isExposed(),
                selectedPricePolicy == null ? null : selectedPricePolicy.getBasePriceAmount(),
                selectedPricePolicy == null ? null : selectedPricePolicy.getAdditionalPersonPriceAmount(),
                activePricePolicies.stream().map(InstructorPricePolicy::getId).toList(),
                complete
        );
    }

    private List<InstructorCertificateType> certificates(InstructorProfile profile) {
        if (profile == null) {
            return List.of();
        }
        return profile.getCertificateTypes().stream()
                .sorted(Comparator.comparing(Enum::name))
                .toList();
    }

    private List<LessonLevel> lessonLevels(InstructorMatchingSetting setting) {
        if (setting == null) {
            return List.of();
        }
        return setting.getLessonLevels().stream()
                .sorted(Comparator.comparing(Enum::name))
                .toList();
    }

    private List<Integer> durations(InstructorMatchingSetting setting) {
        if (setting == null) {
            return List.of();
        }
        return setting.getAvailableDurationMinutes().stream().sorted().toList();
    }

    private List<String> diagnostics(
            Member member,
            InstructorProfile profile,
            InstructorMatchingSetting setting,
            List<InstructorPricePolicy> activePricePolicies,
            boolean configurationComplete,
            boolean consumerFlowActive
    ) {
        List<String> diagnostics = new ArrayList<>();
        if (member.getStatus() != MemberStatus.ACTIVE) {
            diagnostics.add("ACTIVE 회원이 아니어서 강사 동작을 실행할 수 없습니다.");
        }
        if (profile == null) {
            diagnostics.add("강사 신청 프로필이 없습니다. 먼저 테스트 신청을 만드세요.");
            return List.copyOf(diagnostics);
        }
        if (profile.getApprovalStatus() == InstructorApprovalStatus.PENDING) {
            diagnostics.add("강사 승인 대기 상태입니다.");
            if (consumerFlowActive) {
                diagnostics.add("진행 중인 소비자 매칭 또는 확정 강습이 있어 역할 변경 승인을 잠시 막았습니다.");
            }
        }
        if (profile.getApprovalStatus() == InstructorApprovalStatus.APPROVED
                && member.getRole() != MemberRole.INSTRUCTOR) {
            diagnostics.add("APPROVED 프로필과 회원 역할이 일치하지 않습니다.");
        }
        if (profile.getResort() == null) {
            diagnostics.add("활동 리조트가 없습니다.");
        }
        if (setting == null) {
            diagnostics.add("매칭 설정이 없습니다.");
        } else if (!profile.hasCertificateFor(setting.getSport())) {
            diagnostics.add("설정 종목에 맞는 자격증이 없습니다.");
        }
        if (activePricePolicies.size() != 1) {
            diagnostics.add("활성 가격 정책이 정확히 1개가 아닙니다. 설정 저장으로 정리하세요.");
        }
        if (setting != null && setting.isExposed()) {
            diagnostics.add("새 매칭 요청 후보에 노출 중입니다. 기존 제안은 중단해도 취소되지 않습니다.");
        } else if (profile.getApprovalStatus() == InstructorApprovalStatus.APPROVED
                && !configurationComplete) {
            diagnostics.add("매칭 시작에 필요한 설정이 아직 완전하지 않습니다.");
        }
        return List.copyOf(diagnostics);
    }

    private DevInstructorResortOptionResponse toResortOption(Resort resort) {
        return new DevInstructorResortOptionResponse(
                resort.getId(),
                resort.getCode(),
                resort.getDisplayName(),
                resort.getPassFeeAmount()
        );
    }

    private record RelatedState(
            Map<Long, InstructorProfile> profileByMemberId,
            Map<Long, InstructorMatchingSetting> settingByProfileId,
            Map<Long, List<InstructorPricePolicy>> activePricesByProfileId,
            Set<Long> activeConsumerFlowMemberIds
    ) {
        static RelatedState empty() {
            return new RelatedState(Map.of(), Map.of(), Map.of(), Set.of());
        }
    }
}
