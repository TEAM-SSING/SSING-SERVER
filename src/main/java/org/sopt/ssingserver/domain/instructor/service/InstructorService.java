package org.sopt.ssingserver.domain.instructor.service;

import lombok.RequiredArgsConstructor;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstructorService {

    private final InstructorProfileRepository instructorProfileRepository;
    private final InstructorMatchingSettingRepository instructorMatchingSettingRepository;
    private final LessonRepository lessonRepository;

    @Transactional
    public boolean startExposure(
            Long memberId,
            InstructorMatchingExposureRequest request
    ) {
        // InstructorProfile 조회
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

        InstructorMatchingSetting setting = instructorMatchingSettingRepository.findByInstructorProfileId(
                instructorProfile.getId()
        ).orElse(null);

        // 기존 조건이 없으면 새로 만들고, 있으면 요청값으로 덮어씀
        if (setting == null) {
            setting = InstructorMatchingSetting.create(
                    instructorProfile,
                    request.sport(),
                    request.lessonLevels(),
                    request.availableDurationMinutes(),
                    request.maxHeadcount(),
                    request.equipmentReady()
            );
        } else {
            setting.updateConditions(
                    request.sport(),
                    request.lessonLevels(),
                    request.availableDurationMinutes(),
                    request.maxHeadcount(),
                    request.equipmentReady()
            );
        }

        instructorMatchingSettingRepository.save(setting);
        return setting.isExposed();
    }
}
