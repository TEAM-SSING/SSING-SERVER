package org.sopt.ssingserver.domain.lesson.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.sopt.ssingserver.domain.lesson.enums.LessonCancelReason;

public record LessonCancellationRequest(
        @Schema(description = "선택한 취소 사유", example = "SCHEDULE_CHANGED")
        @NotNull(message = "취소 사유는 필수입니다.")
        LessonCancelReason cancelReason,

        @Schema(description = "기타 선택 시 직접 입력한 취소 사유", example = "개인 사정으로 강습을 취소합니다.")
        String cancelReasonDetail
) {

    @AssertTrue(message = "기타 취소 사유를 입력해주세요.")
    @JsonIgnore
    @Schema(hidden = true)
    public boolean isValidEtcDetail() {
        if (cancelReason != LessonCancelReason.ETC) {
            return true;
        }
        return cancelReasonDetail != null && !cancelReasonDetail.isBlank();
    }

    @AssertTrue(message = "기타를 선택한 경우에만 상세 취소 사유를 입력할 수 있습니다.")
    @JsonIgnore
    @Schema(hidden = true)
    public boolean isValidNonEtcDetail() {
        if (cancelReason == null || cancelReason == LessonCancelReason.ETC) {
            return true;
        }
        return cancelReasonDetail == null || cancelReasonDetail.isBlank();
    }
}
