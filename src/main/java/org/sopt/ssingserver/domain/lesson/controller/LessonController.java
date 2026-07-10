package org.sopt.ssingserver.domain.lesson.controller;

import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.lesson.controller.docs.LessonApiDocs;
import org.sopt.ssingserver.domain.lesson.dto.response.ConsumerLessonDetailResponse;
import org.sopt.ssingserver.domain.lesson.dto.response.InstructorLessonDetailResponse;
import org.sopt.ssingserver.domain.lesson.dto.response.LessonCompletionResponse;
import org.sopt.ssingserver.domain.lesson.dto.response.LessonStartConfirmationResponse;
import org.sopt.ssingserver.domain.lesson.response.LessonSuccessCode;
import org.sopt.ssingserver.domain.lesson.service.LessonCompletionService;
import org.sopt.ssingserver.domain.lesson.service.LessonDetailService;
import org.sopt.ssingserver.domain.lesson.service.LessonStartConfirmationService;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.response.CommonSuccessCode;
import org.sopt.ssingserver.global.response.SuccessResponseFactory;
import org.sopt.ssingserver.global.security.access.AccessPolicy;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.sopt.ssingserver.global.security.access.RequireAccess;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class LessonController implements LessonApiDocs {

    private final LessonDetailService lessonDetailService;
    private final LessonStartConfirmationService lessonStartConfirmationService;
    private final LessonCompletionService lessonCompletionService;

    @Override
    @RequireAccess(AccessPolicy.CONSUMER)
    @GetMapping("/consumer/lessons/{lessonId}")
    public ResponseEntity<BaseResponse<ConsumerLessonDetailResponse>> getConsumerLessonDetail(
            CurrentMember currentMember,
            @PathVariable Long lessonId
    ) {
        ConsumerLessonDetailResponse response = lessonDetailService.getConsumerDetail(
                currentMember.memberId(),
                lessonId
        );
        return SuccessResponseFactory.success(CommonSuccessCode.SUCCESS, response);
    }

    @Override
    @RequireAccess(AccessPolicy.APPROVED_INSTRUCTOR)
    @GetMapping("/instructor/lessons/{lessonId}")
    public ResponseEntity<BaseResponse<InstructorLessonDetailResponse>> getInstructorLessonDetail(
            CurrentMember currentMember,
            @PathVariable Long lessonId
    ) {
        InstructorLessonDetailResponse response = lessonDetailService.getInstructorDetail(
                currentMember.memberId(),
                lessonId
        );
        return SuccessResponseFactory.success(CommonSuccessCode.SUCCESS, response);
    }

    @Override
    @RequireAccess(AccessPolicy.ACTIVE_MEMBER)
    @PostMapping("/lessons/{lessonId}/start-confirmation")
    public ResponseEntity<BaseResponse<LessonStartConfirmationResponse>> confirmLessonStart(
            CurrentMember currentMember,
            @PathVariable Long lessonId
    ) {
        LessonStartConfirmationResponse response = lessonStartConfirmationService.confirmStart(
                currentMember,
                lessonId
        );
        LessonSuccessCode successCode = response.started()
                ? LessonSuccessCode.LESSON_STARTED
                : LessonSuccessCode.LESSON_START_CONFIRMATION_PENDING;
        return SuccessResponseFactory.success(successCode, response);
    }

    @Override
    @RequireAccess(AccessPolicy.ACTIVE_MEMBER)
    @PostMapping("/lessons/{lessonId}/completion")
    public ResponseEntity<BaseResponse<LessonCompletionResponse>> completeLesson(
            CurrentMember currentMember,
            @PathVariable Long lessonId
    ) {
        LessonCompletionResponse response = lessonCompletionService.complete(
                currentMember,
                lessonId
        );
        return SuccessResponseFactory.success(LessonSuccessCode.LESSON_COMPLETED, response);
    }
}
