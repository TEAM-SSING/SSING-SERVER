package org.sopt.ssingserver.global.security.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.instructor.repository.InstructorProfileRepository;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.Gender;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.member.repository.MemberRepository;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.security.AuthenticatedMember;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AccessAuthorizationServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private InstructorProfileRepository instructorProfileRepository;

    @Test
    void authorize는_JWT_role이_아니라_DB_role로_CONSUMER를_판단한다() {
        AccessAuthorizationService service = createService();
        AuthenticatedMember principal = new AuthenticatedMember(1L, MemberRole.ADMIN);
        Member member = member(1L, MemberRole.CONSUMER, MemberStatus.ACTIVE);

        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        CurrentMember currentMember = service.authorize(principal, AccessPolicy.CONSUMER);

        assertThat(currentMember.memberId()).isEqualTo(1L);
        assertThat(currentMember.role()).isSameAs(MemberRole.CONSUMER);
        verifyNoInteractions(instructorProfileRepository);
    }

    @Test
    void authorize는_ACTIVE가_아닌_회원이면_FORBIDDEN을_던진다() {
        AccessAuthorizationService service = createService();
        AuthenticatedMember principal = new AuthenticatedMember(1L, MemberRole.CONSUMER);
        Member member = member(1L, MemberRole.CONSUMER, MemberStatus.SUSPENDED);

        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.authorize(principal, AccessPolicy.ACTIVE_MEMBER))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isSameAs(CommonErrorCode.FORBIDDEN));
    }

    @Test
    void authorize는_승인_강사_policy에서_role과_승인상태를_함께_확인한다() {
        AccessAuthorizationService service = createService();
        AuthenticatedMember principal = new AuthenticatedMember(1L, MemberRole.CONSUMER);
        Member member = member(1L, MemberRole.INSTRUCTOR, MemberStatus.ACTIVE);
        InstructorProfile profile = instructorProfile(member, InstructorApprovalStatus.APPROVED);

        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(instructorProfileRepository.findByMemberId(1L)).thenReturn(Optional.of(profile));

        CurrentMember currentMember = service.authorize(principal, AccessPolicy.APPROVED_INSTRUCTOR);

        assertThat(currentMember.role()).isSameAs(MemberRole.INSTRUCTOR);
        assertThat(currentMember.instructorApprovalStatus()).isSameAs(InstructorApprovalStatus.APPROVED);
    }

    @Test
    void authorize는_승인_강사_policy에서_PENDING이면_FORBIDDEN을_던진다() {
        AccessAuthorizationService service = createService();
        AuthenticatedMember principal = new AuthenticatedMember(1L, MemberRole.INSTRUCTOR);
        Member member = member(1L, MemberRole.INSTRUCTOR, MemberStatus.ACTIVE);
        InstructorProfile profile = instructorProfile(member, InstructorApprovalStatus.PENDING);

        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(instructorProfileRepository.findByMemberId(1L)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.authorize(principal, AccessPolicy.APPROVED_INSTRUCTOR))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isSameAs(CommonErrorCode.FORBIDDEN));
    }

    @Test
    void authorize는_대기_강사_policy에서_CONSUMER와_PENDING을_함께_확인한다() {
        AccessAuthorizationService service = createService();
        AuthenticatedMember principal = new AuthenticatedMember(1L, MemberRole.CONSUMER);
        Member member = member(1L, MemberRole.CONSUMER, MemberStatus.ACTIVE);
        InstructorProfile profile = instructorProfile(member, InstructorApprovalStatus.PENDING);

        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(instructorProfileRepository.findByMemberId(1L)).thenReturn(Optional.of(profile));

        CurrentMember currentMember = service.authorize(principal, AccessPolicy.PENDING_INSTRUCTOR);

        assertThat(currentMember.role()).isSameAs(MemberRole.CONSUMER);
        assertThat(currentMember.instructorApprovalStatus()).isSameAs(InstructorApprovalStatus.PENDING);
    }

    @Test
    void authorize는_토큰의_memberId가_DB에_없으면_UNAUTHENTICATED를_던진다() {
        AccessAuthorizationService service = createService();
        AuthenticatedMember principal = new AuthenticatedMember(1L, MemberRole.CONSUMER);

        when(memberRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authorize(principal, AccessPolicy.ACTIVE_MEMBER))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isSameAs(CommonErrorCode.UNAUTHENTICATED));
    }

    private AccessAuthorizationService createService() {
        return new AccessAuthorizationService(memberRepository, instructorProfileRepository);
    }

    private Member member(Long id, MemberRole role, MemberStatus status) {
        Member member = Member.create("테스트회원", null, role, status);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private InstructorProfile instructorProfile(
            Member member,
            InstructorApprovalStatus approvalStatus
    ) {
        return InstructorProfile.create(
                member,
                "테스트강사",
                "010-0000-0000",
                Gender.MALE,
                LocalDate.of(2000, 1, 1),
                "테스트 강사 프로필",
                LocalDate.of(2020, 1, 1),
                approvalStatus,
                approvalStatus == InstructorApprovalStatus.APPROVED ? Instant.parse("2026-07-07T00:00:00Z") : null
        );
    }
}
