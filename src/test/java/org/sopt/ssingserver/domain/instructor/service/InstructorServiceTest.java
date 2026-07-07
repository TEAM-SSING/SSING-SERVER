package org.sopt.ssingserver.domain.instructor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sopt.ssingserver.domain.instructor.dto.request.InstructorMatchingExposureRequest;
import org.sopt.ssingserver.domain.instructor.entity.InstructorMatchingSetting;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.instructor.error.InstructorErrorCode;
import org.sopt.ssingserver.domain.instructor.repository.InstructorMatchingSettingRepository;
import org.sopt.ssingserver.domain.instructor.repository.InstructorProfileRepository;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;
import org.sopt.ssingserver.domain.lesson.repository.LessonRepository;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.Gender;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.sopt.ssingserver.global.error.BusinessException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

@ExtendWith(MockitoExtension.class)
class InstructorServiceTest {

    @Mock
    private InstructorProfileRepository instructorProfileRepository;

    @Mock
    private InstructorMatchingSettingRepository instructorMatchingSettingRepository;

    @Mock
    private LessonRepository lessonRepository;

    @Test
    void startExposure는_새_즉시노출_조건을_저장하고_노출을_시작한다() {
        InstructorService service = createService();
        InstructorProfile profile = instructorProfile(10L, InstructorApprovalStatus.APPROVED);
        InstructorMatchingExposureRequest request = request();

        when(instructorProfileRepository.findByMemberId(1L)).thenReturn(Optional.of(profile));
        when(lessonRepository.existsByInstructorProfileIdAndStatus(
                10L,
                LessonStatus.IN_PROGRESS
        )).thenReturn(false);
        when(instructorMatchingSettingRepository.findByInstructorProfileId(10L)).thenReturn(Optional.empty());

        boolean isExposed = service.startExposure(1L, request);

        ArgumentCaptor<InstructorMatchingSetting> settingCaptor =
                ArgumentCaptor.forClass(InstructorMatchingSetting.class);
        verify(instructorMatchingSettingRepository).save(settingCaptor.capture());

        InstructorMatchingSetting savedSetting = settingCaptor.getValue();
        assertThat(isExposed).isTrue();
        assertThat(savedSetting.getInstructorProfile()).isSameAs(profile);
        assertThat(savedSetting.getSport()).isSameAs(Sport.SNOWBOARD);
        assertThat(savedSetting.getLessonLevels())
                .containsExactlyInAnyOrder(LessonLevel.FIRST_TIME, LessonLevel.BEGINNER);
        assertThat(savedSetting.getAvailableDurationMinutes()).containsExactlyInAnyOrder(120, 180, 240);
        assertThat(savedSetting.getMaxHeadcount()).isEqualTo(3);
        assertThat(savedSetting.isEquipmentReady()).isTrue();
        assertThat(savedSetting.isExposed()).isTrue();
    }

    @Test
    void startExposure는_기존_즉시노출_조건을_요청값으로_덮어쓴다() {
        InstructorService service = createService();
        InstructorProfile profile = instructorProfile(10L, InstructorApprovalStatus.APPROVED);
        InstructorMatchingSetting existingSetting = InstructorMatchingSetting.create(
                profile,
                Sport.SKI,
                List.of(LessonLevel.CERTIFIED),
                List.of(120),
                1,
                true
        );
        existingSetting.stopExposure();
        InstructorMatchingExposureRequest request = request();

        when(instructorProfileRepository.findByMemberId(1L)).thenReturn(Optional.of(profile));
        when(lessonRepository.existsByInstructorProfileIdAndStatus(
                10L,
                LessonStatus.IN_PROGRESS
        )).thenReturn(false);
        when(instructorMatchingSettingRepository.findByInstructorProfileId(10L))
                .thenReturn(Optional.of(existingSetting));

        service.startExposure(1L, request);

        verify(instructorMatchingSettingRepository).save(existingSetting);
        assertThat(existingSetting.getSport()).isSameAs(Sport.SNOWBOARD);
        assertThat(existingSetting.getLessonLevels())
                .containsExactlyInAnyOrder(LessonLevel.FIRST_TIME, LessonLevel.BEGINNER);
        assertThat(existingSetting.getAvailableDurationMinutes()).containsExactlyInAnyOrder(120, 180, 240);
        assertThat(existingSetting.getMaxHeadcount()).isEqualTo(3);
        assertThat(existingSetting.isExposed()).isTrue();
    }

    @Test
    void startExposure는_활성_강습이_있으면_오류를_던진다() {
        InstructorService service = createService();
        InstructorProfile profile = instructorProfile(10L, InstructorApprovalStatus.APPROVED);

        when(instructorProfileRepository.findByMemberId(1L)).thenReturn(Optional.of(profile));
        when(lessonRepository.existsByInstructorProfileIdAndStatus(
                10L,
                LessonStatus.IN_PROGRESS
        )).thenReturn(true);

        assertThatThrownBy(() -> service.startExposure(1L, request()))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isSameAs(InstructorErrorCode.ACTIVE_LESSON_EXISTS));

        verify(instructorMatchingSettingRepository, never()).save(any());
    }

    @Test
    void startExposure는_활동_리조트가_없으면_오류를_던진다() {
        InstructorService service = createService();
        InstructorProfile profile = instructorProfile(10L, InstructorApprovalStatus.APPROVED);
        ReflectionTestUtils.setField(profile, "resort", null);

        when(instructorProfileRepository.findByMemberId(1L)).thenReturn(Optional.of(profile));
        when(lessonRepository.existsByInstructorProfileIdAndStatus(
                10L,
                LessonStatus.IN_PROGRESS
        )).thenReturn(false);

        assertThatThrownBy(() -> service.startExposure(1L, request()))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isSameAs(InstructorErrorCode.INSTRUCTOR_RESORT_NOT_SET));

        verifyNoInteractions(instructorMatchingSettingRepository);
    }

    private InstructorService createService() {
        return new InstructorService(
                instructorProfileRepository,
                instructorMatchingSettingRepository,
                lessonRepository,
                new NoOpTransactionManager()
        );
    }

    private InstructorMatchingExposureRequest request() {
        return new InstructorMatchingExposureRequest(
                Sport.SNOWBOARD,
                List.of(LessonLevel.FIRST_TIME, LessonLevel.BEGINNER),
                List.of(120, 180, 240),
                3,
                true
        );
    }

    private InstructorProfile instructorProfile(
            Long id,
            InstructorApprovalStatus approvalStatus
    ) {
        Member member = Member.create(
                "강사",
                null,
                approvalStatus == InstructorApprovalStatus.APPROVED ? MemberRole.INSTRUCTOR : MemberRole.CONSUMER,
                MemberStatus.ACTIVE
        );
        InstructorProfile profile = InstructorProfile.create(
                member,
                "강사",
                "010-0000-0000",
                Gender.MALE,
                LocalDate.of(2000, 1, 1),
                "테스트 강사 프로필",
                LocalDate.of(2020, 1, 1),
                approvalStatus,
                approvalStatus == InstructorApprovalStatus.APPROVED
                        ? Instant.parse("2026-07-07T00:00:00Z")
                        : null
        );
        ReflectionTestUtils.setField(profile, "id", id);
        ReflectionTestUtils.setField(profile, "resort", resort());
        return profile;
    }

    private Resort resort() {
        try {
            Constructor<Resort> constructor = Resort.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Resort resort = constructor.newInstance();
            ReflectionTestUtils.setField(resort, "code", "HIGH1");
            ReflectionTestUtils.setField(resort, "name", "하이원");
            return resort;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    // TransactionTemplate이 실제 트랜잭션 없이 콜백만 실행하도록 하는 테스트 전용 더미
    private static class NoOpTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
