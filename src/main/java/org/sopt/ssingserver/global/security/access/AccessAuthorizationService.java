package org.sopt.ssingserver.global.security.access;

import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.instructor.repository.InstructorProfileRepository;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.repository.MemberRepository;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.security.AuthenticatedMember;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccessAuthorizationService {

    private static final AccessPolicy[] DEFAULT_POLICIES = {AccessPolicy.ACTIVE_MEMBER};

    private final MemberRepository memberRepository;
    private final InstructorProfileRepository instructorProfileRepository;

    public CurrentMember authorize(
            AuthenticatedMember authenticatedMember,
            AccessPolicy... policies
    ) {
        AccessPolicy[] accessPolicies = normalizePolicies(policies);
        CurrentMember currentMember = getCurrentMember(authenticatedMember);

        if (Arrays.stream(accessPolicies).anyMatch(policy -> policy.isSatisfiedBy(currentMember))) {
            return currentMember;
        }
        throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }

    public CurrentMember getCurrentMember(AuthenticatedMember authenticatedMember) {
        if (authenticatedMember == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHENTICATED);
        }

        // JWT role은 힌트일 뿐이며, 인가 판단은 DB 현재값으로 수행한다.
        Member member = memberRepository.findById(authenticatedMember.memberId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHENTICATED));

        InstructorApprovalStatus instructorApprovalStatus = instructorProfileRepository.findByMemberId(member.getId())
                .map(instructorProfile -> instructorProfile.getApprovalStatus())
                .orElse(null);

        return new CurrentMember(
                member.getId(),
                member.getRole(),
                member.getStatus(),
                instructorApprovalStatus
        );
    }

    private AccessPolicy[] normalizePolicies(AccessPolicy[] policies) {
        if (policies == null || policies.length == 0) {
            return DEFAULT_POLICIES;
        }
        return policies;
    }
}
