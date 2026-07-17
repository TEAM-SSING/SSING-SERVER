package org.sopt.ssingserver.domain.instructor.dev.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import org.sopt.ssingserver.domain.member.enums.Gender;

@Schema(description = "신청 만들기에서 자동 입력되는 테스트 프로필 값")
public record DevInstructorApplicationDefaultsResponse(
        @Schema(description = "실명 입력 규칙", example = "카카오 닉네임 사용")
        String realNameRule,
        @Schema(description = "고정 테스트 전화번호", example = "010-0000-0000")
        String phone,
        @Schema(description = "고정 테스트 성별", example = "MALE")
        Gender gender,
        @Schema(description = "고정 테스트 생년월일", example = "2000-01-01")
        LocalDate birthDate,
        @Schema(description = "고정 테스트 소개")
        String intro,
        @Schema(description = "고정 테스트 경력 시작일", example = "2020-01-01")
        LocalDate careerStartDate
) {
}
