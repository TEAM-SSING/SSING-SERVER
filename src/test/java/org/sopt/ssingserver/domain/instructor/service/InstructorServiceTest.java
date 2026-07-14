package org.sopt.ssingserver.domain.instructor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sopt.ssingserver.domain.instructor.dto.request.InstructorMatchingExposureRequest;
import org.sopt.ssingserver.domain.instructor.dto.response.InstructorMatchingExposureCancelResponse;
import org.sopt.ssingserver.domain.instructor.dto.response.InstructorMatchingExposureResponse;
import org.sopt.ssingserver.domain.instructor.dto.result.InstructorMatchingExposureConditionsResult;
import org.sopt.ssingserver.domain.instructor.entity.InstructorMatchingSetting;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.instructor.enums.InstructorCertificateType;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.instructor.error.InstructorErrorCode;
import org.sopt.ssingserver.domain.instructor.repository.InstructorMatchingSettingRepository;
import org.sopt.ssingserver.domain.instructor.repository.InstructorProfileRepository;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;
import org.sopt.ssingserver.domain.lesson.repository.LessonRepository;
import org.sopt.ssingserver.domain.matching.service.MatchingAfterCommitExecutor;
import org.sopt.ssingserver.domain.matching.service.MatchingSearchTriggerService;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.Gender;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.BusinessValidationException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

@ExtendWith(MockitoExtension.class)
class InstructorServiceTest {

    private static final Instant FIXED_UPDATED_AT = Instant.parse("2026-07-07T00:00:00Z");

    @Mock
    private InstructorProfileRepository instructorProfileRepository;

    @Mock
    private InstructorMatchingSettingRepository instructorMatchingSettingRepository;

    @Mock
    private LessonRepository lessonRepository;

    @Mock
    private MatchingSearchTriggerService matchingSearchTriggerService;

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
        when(instructorMatchingSettingRepository.existsByInstructorProfileId(10L)).thenReturn(false);

        InstructorMatchingExposureResponse response = service.startExposure(1L, request);

        ArgumentCaptor<InstructorMatchingSetting> settingCaptor =
                ArgumentCaptor.forClass(InstructorMatchingSetting.class);
        verify(instructorMatchingSettingRepository).save(settingCaptor.capture());
        verify(instructorMatchingSettingRepository, never()).findByInstructorProfileIdForUpdate(10L);

        InstructorMatchingSetting savedSetting = settingCaptor.getValue();
        assertThat(response.isExposed()).isTrue();
        assertThat(savedSetting.getInstructorProfile()).isSameAs(profile);
        assertThat(savedSetting.getSport()).isSameAs(Sport.SNOWBOARD);
        assertThat(savedSetting.getLessonLevels())
                .containsExactlyInAnyOrder(LessonLevel.FIRST_TIME, LessonLevel.BEGINNER);
        assertThat(savedSetting.getAvailableDurationMinutes()).containsExactlyInAnyOrder(120, 180, 240);
        assertThat(savedSetting.getMaxHeadcount()).isEqualTo(3);
        assertThat(savedSetting.isEquipmentReady()).isTrue();
        assertThat(savedSetting.isExposed()).isTrue();
        verify(matchingSearchTriggerService).triggerAllRequested();
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
        when(instructorMatchingSettingRepository.existsByInstructorProfileId(10L)).thenReturn(true);
        when(instructorMatchingSettingRepository.findByInstructorProfileIdForUpdate(10L))
                .thenReturn(Optional.of(existingSetting));

        service.startExposure(1L, request);

        verify(instructorMatchingSettingRepository).findByInstructorProfileIdForUpdate(10L);
        verify(instructorMatchingSettingRepository).save(existingSetting);
        assertThat(existingSetting.getSport()).isSameAs(Sport.SNOWBOARD);
        assertThat(existingSetting.getLessonLevels())
                .containsExactlyInAnyOrder(LessonLevel.FIRST_TIME, LessonLevel.BEGINNER);
        assertThat(existingSetting.getAvailableDurationMinutes()).containsExactlyInAnyOrder(120, 180, 240);
        assertThat(existingSetting.getMaxHeadcount()).isEqualTo(3);
        assertThat(existingSetting.isExposed()).isTrue();
        verify(matchingSearchTriggerService).triggerAllRequested();
    }

    @Test
    void cancelExposure는_기존_즉시노출_조건의_노출을_중단한다() {
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

        when(instructorProfileRepository.findByMemberId(1L)).thenReturn(Optional.of(profile));
        when(instructorMatchingSettingRepository.findByInstructorProfileIdForUpdate(10L))
                .thenReturn(Optional.of(existingSetting));
        when(instructorMatchingSettingRepository.saveAndFlush(existingSetting)).thenAnswer(invocation -> {
            ReflectionTestUtils.setField(existingSetting, "updatedAt", FIXED_UPDATED_AT);
            return existingSetting;
        });

        InstructorMatchingExposureCancelResponse response = service.cancelExposure(1L);

        verify(instructorMatchingSettingRepository).findByInstructorProfileIdForUpdate(10L);
        verify(instructorMatchingSettingRepository).saveAndFlush(existingSetting);
        assertThat(response.isExposed()).isFalse();
        assertThat(response.updatedAt()).isEqualTo(FIXED_UPDATED_AT.atOffset(ZoneOffset.ofHours(9)));
        assertThat(existingSetting.isExposed()).isFalse();
        verify(matchingSearchTriggerService, never()).triggerAllRequested();
    }

    @Test
    void cancelExposure는_이미_중단된_즉시노출_조건을_성공으로_처리한다() {
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
        ReflectionTestUtils.setField(existingSetting, "updatedAt", FIXED_UPDATED_AT);

        when(instructorProfileRepository.findByMemberId(1L)).thenReturn(Optional.of(profile));
        when(instructorMatchingSettingRepository.findByInstructorProfileIdForUpdate(10L))
                .thenReturn(Optional.of(existingSetting));

        InstructorMatchingExposureCancelResponse response = service.cancelExposure(1L);

        verify(instructorMatchingSettingRepository, never()).saveAndFlush(any());
        assertThat(response.isExposed()).isFalse();
        assertThat(response.updatedAt()).isEqualTo(FIXED_UPDATED_AT.atOffset(ZoneOffset.ofHours(9)));
        assertThat(existingSetting.isExposed()).isFalse();
        verify(matchingSearchTriggerService, never()).triggerAllRequested();
    }

    @Test
    void cancelExposure는_즉시노출_조건이_없으면_오류를_던진다() {
        InstructorService service = createService();
        InstructorProfile profile = instructorProfile(10L, InstructorApprovalStatus.APPROVED);

        when(instructorProfileRepository.findByMemberId(1L)).thenReturn(Optional.of(profile));
        when(instructorMatchingSettingRepository.findByInstructorProfileIdForUpdate(10L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelExposure(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isSameAs(CommonErrorCode.NOT_FOUND));

        verify(instructorMatchingSettingRepository, never()).saveAndFlush(any());
        verify(matchingSearchTriggerService, never()).triggerAllRequested();
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
        verify(matchingSearchTriggerService, never()).triggerAllRequested();
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
        verify(matchingSearchTriggerService, never()).triggerAllRequested();
    }

    @Test
    void startExposure는_보유_자격증에_없는_종목이면_저장하지_않는다() {
        InstructorService service = createService();
        InstructorProfile profile = instructorProfile(10L, InstructorApprovalStatus.APPROVED);
        ReflectionTestUtils.setField(
                profile,
                "certificateType",
                InstructorCertificateType.KSIA_SKI_LEVEL_1
        );

        when(instructorProfileRepository.findByMemberId(1L)).thenReturn(Optional.of(profile));
        when(lessonRepository.existsByInstructorProfileIdAndStatus(
                10L,
                LessonStatus.IN_PROGRESS
        )).thenReturn(false);

        assertThatThrownBy(() -> service.startExposure(1L, request()))
                .isInstanceOf(BusinessValidationException.class)
                .satisfies(exception -> {
                    BusinessValidationException validationException =
                            (BusinessValidationException) exception;
                    assertThat(validationException.getErrorCode())
                            .isSameAs(CommonErrorCode.VALIDATION_FAILED);
                    assertThat(validationException.getErrors())
                            .containsEntry("sport", "보유 자격증으로 선택할 수 없는 종목입니다.");
                });

        verifyNoInteractions(instructorMatchingSettingRepository);
        verify(matchingSearchTriggerService, never()).triggerAllRequested();
    }

    @Test
    void getExposureConditions는_활동_리조트와_자격증_종목만_반환한다() {
        InstructorService service = createService();
        InstructorProfile profile = instructorProfile(10L, InstructorApprovalStatus.APPROVED);
        ReflectionTestUtils.setField(
                profile,
                "certificateType",
                InstructorCertificateType.KSIA_SKI_LEVEL_1
        );
        profile.registerCertificate(InstructorCertificateType.KSIA_SKI_LEVEL_1);

        when(instructorProfileRepository.findByMemberId(1L)).thenReturn(Optional.of(profile));

        InstructorMatchingExposureConditionsResult result = service.getExposureConditions(1L);

        assertThat(result.resort().code()).isEqualTo("HIGH1");
        assertThat(result.resort().displayName()).isEqualTo("하이원");
        assertThat(result.availableSports()).containsExactly(Sport.SKI);
        verifyNoInteractions(
                instructorMatchingSettingRepository,
                lessonRepository,
                matchingSearchTriggerService
        );
    }

    @Test
    void getExposureConditions는_자격증이_없으면_빈_종목목록을_반환한다() {
        InstructorService service = createService();
        InstructorProfile profile = instructorProfile(10L, InstructorApprovalStatus.APPROVED);
        ReflectionTestUtils.setField(profile, "certificateType", null);

        when(instructorProfileRepository.findByMemberId(1L)).thenReturn(Optional.of(profile));

        InstructorMatchingExposureConditionsResult result = service.getExposureConditions(1L);

        assertThat(result.availableSports()).isEmpty();
        verifyNoInteractions(
                instructorMatchingSettingRepository,
                lessonRepository,
                matchingSearchTriggerService
        );
    }

    @Test
    void getExposureConditions는_활동_리조트가_없으면_저장조건을_조회하지_않는다() {
        InstructorService service = createService();
        InstructorProfile profile = instructorProfile(10L, InstructorApprovalStatus.APPROVED);
        ReflectionTestUtils.setField(profile, "resort", null);

        when(instructorProfileRepository.findByMemberId(1L)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.getExposureConditions(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isSameAs(InstructorErrorCode.INSTRUCTOR_RESORT_NOT_SET));

        verifyNoInteractions(instructorMatchingSettingRepository, lessonRepository, matchingSearchTriggerService);
    }

    @Test
    void startExposure는_동시생성_충돌_재시도에서_생성된_설정을_잠그고_갱신한다() {
        InstructorService service = createService();
        InstructorProfile profile = instructorProfile(10L, InstructorApprovalStatus.APPROVED);
        InstructorMatchingSetting concurrentlyCreatedSetting = InstructorMatchingSetting.create(
                profile,
                Sport.SKI,
                List.of(LessonLevel.CERTIFIED),
                List.of(120),
                1,
                true
        );
        concurrentlyCreatedSetting.stopExposure();

        when(instructorProfileRepository.findByMemberId(1L))
                .thenReturn(Optional.of(profile), Optional.of(profile));
        when(lessonRepository.existsByInstructorProfileIdAndStatus(
                10L,
                LessonStatus.IN_PROGRESS
        )).thenReturn(false);
        when(instructorMatchingSettingRepository.existsByInstructorProfileId(10L)).thenReturn(false);
        when(instructorMatchingSettingRepository.findByInstructorProfileIdForUpdate(10L))
                .thenReturn(Optional.of(concurrentlyCreatedSetting));
        when(instructorMatchingSettingRepository.save(any(InstructorMatchingSetting.class)))
                .thenThrow(new DataIntegrityViolationException("concurrent insert"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InstructorMatchingExposureResponse response = service.startExposure(1L, request());

        assertThat(response.isExposed()).isTrue();
        assertThat(concurrentlyCreatedSetting.getSport()).isSameAs(Sport.SNOWBOARD);
        assertThat(concurrentlyCreatedSetting.getLessonLevels())
                .containsExactlyInAnyOrder(LessonLevel.FIRST_TIME, LessonLevel.BEGINNER);
        assertThat(concurrentlyCreatedSetting.getAvailableDurationMinutes())
                .containsExactlyInAnyOrder(120, 180, 240);
        verify(instructorMatchingSettingRepository).findByInstructorProfileIdForUpdate(10L);
        verify(instructorMatchingSettingRepository, times(2)).save(any(InstructorMatchingSetting.class));
        verify(matchingSearchTriggerService).triggerAllRequested();
    }

    @Test
    void startExposure는_동시생성_충돌_재시도에서도_최신_자격증으로_종목을_재검증한다() {
        InstructorService service = createService();
        InstructorProfile firstProfile = instructorProfile(10L, InstructorApprovalStatus.APPROVED);
        InstructorProfile changedProfile = instructorProfile(10L, InstructorApprovalStatus.APPROVED);
        ReflectionTestUtils.setField(
                changedProfile,
                "certificateType",
                InstructorCertificateType.KSIA_SKI_LEVEL_1
        );

        when(instructorProfileRepository.findByMemberId(1L))
                .thenReturn(Optional.of(firstProfile), Optional.of(changedProfile));
        when(lessonRepository.existsByInstructorProfileIdAndStatus(
                10L,
                LessonStatus.IN_PROGRESS
        )).thenReturn(false);
        when(instructorMatchingSettingRepository.existsByInstructorProfileId(10L)).thenReturn(false);
        when(instructorMatchingSettingRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("concurrent insert"));

        assertThatThrownBy(() -> service.startExposure(1L, request()))
                .isInstanceOf(BusinessValidationException.class)
                .satisfies(exception -> assertThat(((BusinessValidationException) exception).getErrors())
                        .containsEntry("sport", "보유 자격증으로 선택할 수 없는 종목입니다."));

        verify(instructorMatchingSettingRepository, times(1)).save(any());
        verify(matchingSearchTriggerService, never()).triggerAllRequested();
    }

    private InstructorService createService() {
        return new InstructorService(
                instructorProfileRepository,
                instructorMatchingSettingRepository,
                lessonRepository,
                matchingSearchTriggerService,
                new MatchingAfterCommitExecutor(),
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
        ReflectionTestUtils.setField(
                profile,
                "certificateType",
                InstructorCertificateType.KSIA_SNOWBOARD_LEVEL_1
        );
        return profile;
    }

    private Resort resort() {
        try {
            Constructor<Resort> constructor = Resort.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Resort resort = constructor.newInstance();
            ReflectionTestUtils.setField(resort, "code", "HIGH1");
            ReflectionTestUtils.setField(resort, "name", "하이원");
            ReflectionTestUtils.setField(resort, "displayName", "하이원");
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
