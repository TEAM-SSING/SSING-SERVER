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
import org.sopt.ssingserver.domain.matching.service.MatchingAfterCommitExecutor;
import org.sopt.ssingserver.domain.matching.service.MatchingSearchTriggerService;
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
    private final MatchingSearchTriggerService matchingSearchTriggerService;
    private final MatchingAfterCommitExecutor matchingAfterCommitExecutor;
    private final TransactionTemplate transactionTemplate;

    public InstructorService(
            InstructorProfileRepository instructorProfileRepository,
            InstructorMatchingSettingRepository instructorMatchingSettingRepository,
            LessonRepository lessonRepository,
            MatchingSearchTriggerService matchingSearchTriggerService,
            MatchingAfterCommitExecutor matchingAfterCommitExecutor,
            PlatformTransactionManager transactionManager
    ) {
        this.instructorProfileRepository = instructorProfileRepository;
        this.instructorMatchingSettingRepository = instructorMatchingSettingRepository;
        this.lessonRepository = lessonRepository;
        this.matchingSearchTriggerService = matchingSearchTriggerService;
        this.matchingAfterCommitExecutor = matchingAfterCommitExecutor;
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
            return updateAfterConflict(memberId, request);
        }
    }

    // 단일 트랜잭션에서 검증과 조건 생성/갱신을 수행
    private boolean startExposureInTransaction(
            Long memberId,
            InstructorMatchingExposureRequest request
    ) {
        return saveExposureSetting(memberId, request, true);
    }

    // 무결성 오류로 실패한 트랜잭션과 분리하여, 동시에 먼저 생성된 설정을 다시 조회해 요청값으로 덮어씀
    private boolean updateAfterConflict(
            Long memberId,
            InstructorMatchingExposureRequest request
    ) {
        return Objects.requireNonNull(
                transactionTemplate.execute(status -> saveExposureSetting(memberId, request, false))
        );
    }

    // 강사 프로필 조회, 노출 가능 검증, 조건 생성/갱신, 커밋 후 재탐색 예약의 공통 흐름
    private boolean saveExposureSetting(
            Long memberId,
            InstructorMatchingExposureRequest request,
            boolean createWhenMissing
    ) {
        InstructorProfile instructorProfile = findInstructorProfile(memberId);
        validateExposureAllowed(instructorProfile);

        InstructorMatchingSetting setting = findOrCreateMatchingSetting(
                instructorProfile,
                request,
                createWhenMissing
        );
        instructorMatchingSettingRepository.save(setting);
        triggerRequestedSearchAfterCommit();
        return setting.isExposed();
    }

    // 인증 회원의 강사 프로필 필수 존재 조회
    private InstructorProfile findInstructorProfile(Long memberId) {
        return instructorProfileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    // 기존 조건이 있으면 요청값 갱신, 없으면 최초 저장 경로에서만 새 조건 생성
    private InstructorMatchingSetting findOrCreateMatchingSetting(
            InstructorProfile instructorProfile,
            InstructorMatchingExposureRequest request,
            boolean createWhenMissing
    ) {
        return instructorMatchingSettingRepository.findByInstructorProfileId(instructorProfile.getId())
                .map(existingSetting -> updateMatchingSetting(existingSetting, request))
                .orElseGet(() -> createMatchingSetting(instructorProfile, request, createWhenMissing));
    }

    // 기존 즉시 노출 조건의 요청값 덮어쓰기와 노출 ON 전환
    private InstructorMatchingSetting updateMatchingSetting(
            InstructorMatchingSetting setting,
            InstructorMatchingExposureRequest request
    ) {
        setting.updateConditions(
                request.sport(),
                request.lessonLevels(),
                request.availableDurationMinutes(),
                request.maxHeadcount(),
                request.equipmentReady()
        );
        return setting;
    }

    // 동시 생성 충돌 복구 경로와 최초 생성 경로의 생성 가능 여부 분리
    private InstructorMatchingSetting createMatchingSetting(
            InstructorProfile instructorProfile,
            InstructorMatchingExposureRequest request,
            boolean createWhenMissing
    ) {
        if (!createWhenMissing) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }

        return InstructorMatchingSetting.create(
                instructorProfile,
                request.sport(),
                request.lessonLevels(),
                request.availableDurationMinutes(),
                request.maxHeadcount(),
                request.equipmentReady()
        );
    }

    // 노출 조건 저장 커밋 이후 SEARCHING 요청 전체 재탐색 예약
    private void triggerRequestedSearchAfterCommit() {
        matchingAfterCommitExecutor.execute(
                "instructor-matching-exposure-search",
                matchingSearchTriggerService::triggerAllRequested
        );
    }

    // 진행 중인 강습 여부와 활동 리조트 등록 여부를 확인
    private void validateExposureAllowed(InstructorProfile instructorProfile) {
        if (lessonRepository.existsByInstructorProfileIdAndStatus(
                instructorProfile.getId(),
                LessonStatus.IN_PROGRESS
        )) {
            throw new BusinessException(InstructorErrorCode.ACTIVE_LESSON_EXISTS);
        }

        if (instructorProfile.getResort() == null) {
            throw new BusinessException(InstructorErrorCode.INSTRUCTOR_RESORT_NOT_SET);
        }
    }
}
