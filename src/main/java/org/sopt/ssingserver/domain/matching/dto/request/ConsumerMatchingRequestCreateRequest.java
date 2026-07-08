package org.sopt.ssingserver.domain.matching.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.matching.dto.command.MatchingCreationCommand;
import org.sopt.ssingserver.domain.matching.dto.command.MatchingParticipantCommand;
import org.sopt.ssingserver.domain.member.enums.Gender;
import org.sopt.ssingserver.global.validation.ValidRequestedDurations;

public record ConsumerMatchingRequestCreateRequest(
        @Schema(description = "리조트 코드", example = "HIGH1")
        @NotNull(message = "리조트는 필수입니다.")
        String resort,

        @Schema(description = "희망 종목", example = "SNOWBOARD")
        @NotNull(message = "종목은 필수입니다.")
        Sport sport,

        @Schema(description = "강습 레벨", example = "FIRST_TIME")
        @NotNull(message = "강습 레벨은 필수입니다.")
        LessonLevel lessonLevel,

        @Schema(description = "희망 강습 시간 목록", example = "[120, 180]")
        @NotEmpty(message = "희망 강습 시간은 1개 이상 선택해야 합니다.")
        @ValidRequestedDurations(
                allowedValues = {120, 180, 240},
                notAllowedMessage = "희망 강습 시간은 120, 180, 240분 중에서 선택해야 합니다.",
                duplicatedMessage = "희망 강습 시간은 중복 없이 선택해야 합니다."
        )
        List<@NotNull(message = "희망 강습 시간은 null일 수 없습니다.") Integer> requestedDurationMinutes,

        @Schema(description = "참여자 정보 목록")
        @NotEmpty(message = "참여자는 1명 이상이어야 합니다.")
        List<@Valid @NotNull(message = "참여자 정보는 null일 수 없습니다.") ParticipantRequest> participants,

        @Schema(description = "장비 착용 완료 여부", example = "true")
        @NotNull(message = "장비 착용 완료 여부는 필수입니다.")
        Boolean equipmentReady
) {

    public MatchingCreationCommand toCommand(Long memberId) {
        return MatchingCreationCommand.of(
                memberId,
                resort,
                sport,
                lessonLevel,
                requestedDurationMinutes,
                equipmentReady,
                participants.stream()
                        .map(ParticipantRequest::toCommand)
                        .toList()
        );
    }

    public record ParticipantRequest(
            @Schema(description = "참여자 나이", example = "24")
            @NotNull(message = "참여자 나이는 필수입니다.")
            @Min(value = 1, message = "참여자 나이는 1세 이상이어야 합니다.")
            Integer age,

            @Schema(description = "참여자 성별", example = "FEMALE")
            @NotNull(message = "참여자 성별은 필수입니다.")
            Gender gender
    ) {

        private MatchingParticipantCommand toCommand() {
            return MatchingParticipantCommand.of(age, gender);
        }
    }
}
