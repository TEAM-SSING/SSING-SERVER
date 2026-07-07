package org.sopt.ssingserver.domain.instructor.service;

import java.util.Objects;
import org.sopt.ssingserver.domain.instructor.dto.request.InstructorMatchingExposureRequest;
import org.sopt.ssingserver.domain.instructor.entity.InstructorMatchingSetting;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.instructor.error.InstructorErrorCode;
import org.sopt.ssingserver.domain.instructor.repository.InstructorMatchingSettingRepository;
import org.sopt.ssingserver.domain.instructor.repository.InstructorProfileRepository;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;
import org.sopt.ssingserver.domain.lesson.repository.LessonRepository;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class InstructorService {

    private final InstructorProfileRepository instructorProfileRepository;
    private final InstructorMatchingSettingRepository instructorMatchingSettingRepository;
    private final LessonRepository lessonRepository;
    private final TransactionTemplate transactionTemplate;

    public InstructorService(
            InstructorProfileRepository instructorProfileRepository,
            InstructorMatchingSettingRepository instructorMatchingSettingRepository,
            LessonRepository lessonRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.instructorProfileRepository = instructorProfileRepository;
        this.instructorMatchingSettingRepository = instructorMatchingSettingRepository;
        this.lessonRepository = lessonRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public boolean startExposure(
            Long memberId,
            InstructorMatchingExposureRequest request
    ) {
        try {
            return Objects.requireNonNull(
                    transactionTemplate.execute(status -> startExposureInTransaction(memberId, request))
            );
        } catch (DataIntegrityViolationException exception) {
            return updateAfterConflict(memberId, request, exception);
        }
    }

    // 단일 트랜잭션에서 검증과 조건 생성/갱신을 수행
    private boolean startExposureInTransaction(
            Long memberId,
            InstructorMatchingExposureRequest request
    ) {
        InstructorProfile instructorProfile = instructorProfileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        // 진행 중인 강습이 있으면 즉시 매칭 불가능
        if (lessonRepository.existsByInstructorProfileIdAndStatus(
                instructorProfile.getId(),
                LessonStatus.IN_PROGRESS
        )) {
            throw new BusinessException(InstructorErrorCode.ACTIVE_LESSON_EXISTS);
        }

        // 강사 활동 리조트 확인
        if (instructorProfile.getResort() == null) {
            throw new BusinessException(InstructorErrorCode.INSTRUCTOR_RESORT_NOT_SET);
        }

        // 기존 조건이 없으면 새로 만들고, 있으면 요청값으로 덮어씀
        InstructorMatchingSetting setting = instructorMatchingSettingRepository
                .findByInstructorProfileId(instructorProfile.getId())
                .map(existingSetting -> {
                    existingSetting.updateConditions(
                            request.sport(),
                            request.lessonLevels(),
                            request.availableDurationMinutes(),
                            request.maxHeadcount(),
                            request.equipmentReady()
                    );
                    return existingSetting;
                })
                .orElseGet(() -> InstructorMatchingSetting.create(
                        instructorProfile,
                        request.sport(),
                        request.lessonLevels(),
                        request.availableDurationMinutes(),
                        request.maxHeadcount(),
                        request.equipmentReady()
                ));

        instructorMatchingSettingRepository.save(setting);
        return setting.isExposed();
    }

    // 무결성 오류로 실패한 트랜잭션과 분리하여, 동시에 먼저 생성된 설정을 다시 조회해 요청값으로 덮어씀
    private boolean updateAfterConflict(
            Long memberId,
            InstructorMatchingExposureRequest request,
            DataIntegrityViolationException originalException
    ) {
        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            InstructorProfile instructorProfile = instructorProfileRepository.findByMemberId(memberId)
                    .orElseThrow(() -> originalException);
            InstructorMatchingSetting setting = instructorMatchingSettingRepository
                    .findByInstructorProfileId(instructorProfile.getId())
                    .orElseThrow(() -> originalException);

            setting.updateConditions(
                    request.sport(),
                    request.lessonLevels(),
                    request.availableDurationMinutes(),
                    request.maxHeadcount(),
                    request.equipmentReady()
            );
            return setting.isExposed();
        }));
    }
}
