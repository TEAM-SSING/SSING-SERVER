package org.sopt.ssingserver.domain.lesson.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.sopt.ssingserver.domain.lesson.dto.response.ConsumerLessonDetailResponse;
import org.sopt.ssingserver.domain.lesson.dto.response.InstructorLessonDetailResponse;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "Lesson", description = "강습 API")
public interface LessonApiDocs {

    @Operation(
            summary = "소비자 강습 상세 조회",
            description = "회원 앱에서 강습 상태에 따른 강습 상세 정보를 조회합니다.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "강습 상세 조회 성공")
    ResponseEntity<BaseResponse<ConsumerLessonDetailResponse>> getConsumerLessonDetail(
            @Parameter(hidden = true)
            CurrentMember currentMember,
            @Parameter(description = "강습 ID")
            @PathVariable Long lessonId
    );

    @Operation(
            summary = "강사 강습 상세 조회",
            description = "강사 앱에서 강습 상태에 따른 강습 상세 정보를 조회합니다.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "강습 상세 조회 성공")
    ResponseEntity<BaseResponse<InstructorLessonDetailResponse>> getInstructorLessonDetail(
            @Parameter(hidden = true)
            CurrentMember currentMember,
            @Parameter(description = "강습 ID")
            @PathVariable Long lessonId
    );
}
