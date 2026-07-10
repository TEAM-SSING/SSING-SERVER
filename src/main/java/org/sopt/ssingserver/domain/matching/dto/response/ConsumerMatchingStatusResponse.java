package org.sopt.ssingserver.domain.matching.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingStatusQueryResult;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupItemStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.member.enums.Gender;
import org.sopt.ssingserver.domain.payment.enums.MatchingRequestPaymentStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConsumerMatchingStatusResponse(
        @Schema(description = "매칭 요청 ID", example = "10")
        Long matchingRequestId,

        @Schema(description = "Android 화면 전환용 매칭 상태", example = "WAITING_FOR_CONFIRMATION")
        MatchingStatus matchingStatus,

        @Schema(description = "매칭 요청 DB 상태", example = "MATCHED")
        MatchingRequestStatus requestStatus,

        @Schema(description = "매칭 요청 상태 변경 사유. 사유가 있을 때만 포함", example = "CONSUMER_REJECTED_INSTRUCTOR")
        MatchingRequestStatusReason requestStatusReason,

        @Schema(description = "매칭 요청 그룹 ID. 그룹이 생성된 경우에만 포함", example = "3")
        Long groupId,

        @Schema(description = "매칭 요청 그룹 DB 상태. 그룹이 생성된 경우에만 포함", example = "INSTRUCTOR_ACCEPTED")
        MatchingRequestGroupStatus groupStatus,

        @Schema(description = "그룹 안에서 현재 요청자의 상태. 그룹 항목이 생성된 경우에만 포함", example = "PENDING")
        MatchingRequestGroupItemStatus itemStatus,

        @Schema(description = "강사 제안 상태. 제안이 생성된 경우에만 포함", example = "ACCEPTED")
        MatchingOfferStatus offerStatus,

        @Schema(description = "현재 요청자의 결제 상태. 결제 요청이 생성된 경우에만 포함", example = "PENDING")
        MatchingRequestPaymentStatus paymentStatus,

        @Schema(description = "현재 상태의 만료 또는 다음 전환 기준 시각. 무기한 정책에서는 생략", example = "2026-06-28T15:35:00+09:00")
        Instant expiresAt,

        @Schema(description = "강사가 수락한 뒤 포함되는 강사 프로필 요약. 값이 없으면 응답에서 생략")
        InstructorProfileResponse instructorProfile,

        @Schema(description = "생성된 강습 ID. 매칭이 확정된 경우에만 포함", example = "30")
        Long lessonId
) {

    public static ConsumerMatchingStatusResponse from(MatchingStatusQueryResult result) {
        return new ConsumerMatchingStatusResponse(
                result.matchingRequestId(),
                result.matchingStatus(),
                result.requestStatus(),
                result.requestStatusReason(),
                result.groupId(),
                result.groupStatus(),
                result.itemStatus(),
                result.offerStatus(),
                result.paymentStatus(),
                result.expiresAt(),
                InstructorProfileResponse.from(result.instructorProfile()),
                result.lessonId()
        );
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(
            name = "ConsumerMatchingStatusInstructorProfile",
            description = "소비자 매칭 화면에 표시하는 강사 프로필 요약"
    )
    public record InstructorProfileResponse(
            @Schema(description = "강사 프로필 ID", example = "7")
            Long instructorId,

            @Schema(description = "강사 표시 이름", example = "김OO")
            String name,

            @Schema(description = "강사 프로필 이미지 URL. 이미지가 있을 때만 포함", example = "https://example.com/instructors/7/profile.jpg")
            String profileImageUrl,

            @Schema(description = "강사 성별", example = "MALE")
            Gender gender,

            @Schema(description = "강사 출생연도", example = "1994")
            Integer birthYear,

            @Schema(description = "강사 프로필에 직접 저장된 레벨. 자격증 종류는 응답하지 않음", example = "2")
            Integer level
    ) {

        private static InstructorProfileResponse from(MatchingStatusQueryResult.InstructorProfileResult result) {
            if (result == null) {
                return null;
            }

            return new InstructorProfileResponse(
                    result.instructorId(),
                    result.name(),
                    result.profileImageUrl(),
                    result.gender(),
                    result.birthYear(),
                    result.level()
            );
        }
    }
}
