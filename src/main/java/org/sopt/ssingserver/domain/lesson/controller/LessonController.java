package org.sopt.ssingserver.domain.lesson.controller;

import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.lesson.controller.docs.LessonApiDocs;
import org.sopt.ssingserver.domain.lesson.dto.response.ConsumerLessonDetailResponse;
import org.sopt.ssingserver.domain.lesson.service.LessonDetailService;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.response.CommonSuccessCode;
import org.sopt.ssingserver.global.response.SuccessResponseFactory;
import org.sopt.ssingserver.global.security.access.AccessPolicy;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.sopt.ssingserver.global.security.access.RequireAccess;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class LessonController implements LessonApiDocs {

    private final LessonDetailService lessonDetailService;

    @Override
    @RequireAccess(AccessPolicy.CONSUMER)
    @GetMapping("/consumer/lessons/{lessonId}")
    public ResponseEntity<BaseResponse<ConsumerLessonDetailResponse>> getConsumerLessonDetail(
            CurrentMember currentMember,
            @PathVariable Long lessonId
    ) {
        ConsumerLessonDetailResponse response = lessonDetailService.getDetail(
                currentMember.memberId(),
                lessonId
        );
        return SuccessResponseFactory.success(CommonSuccessCode.SUCCESS, response);
    }
}
