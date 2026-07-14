package org.sopt.ssingserver.domain.matching.dto.result;

import java.time.Instant;
import java.time.LocalDate;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupItemStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.Gender;
import org.sopt.ssingserver.domain.payment.enums.MatchingRequestPaymentStatus;

// 소비자 상태 조회 API가 필요한 DB 현재값들을 서비스 경계에서 한 번에 전달하는 결과
public record MatchingStatusQueryResult(
        Long matchingRequestId,
        MatchingStatus matchingStatus,
        MatchingRequestStatus requestStatus,
        MatchingRequestStatusReason requestStatusReason,
        Long groupId,
        MatchingRequestGroupStatus groupStatus,
        MatchingRequestGroupItemStatus itemStatus,
        MatchingOfferStatus offerStatus,
        MatchingRequestPaymentStatus paymentStatus,
        MatchingProgressSummaryResult progressSummary,
        Instant expiresAt,
        InstructorProfileResult instructorProfile,
        Long lessonId,
        MatchingPriceSummaryResult priceSummary
) {

    public record InstructorProfileResult(
            Long instructorId,
            String name,
            String profileImageUrl,
            Gender gender,
            Integer birthYear,
            Integer level
    ) {

        public static InstructorProfileResult from(InstructorProfile instructorProfile) {
            Member member = instructorProfile.getMember();
            LocalDate birthDate = instructorProfile.getBirthDate();
            return new InstructorProfileResult(
                    instructorProfile.getId(),
                    instructorProfile.getRealName(),
                    member == null ? null : member.getProfileImageUrl(),
                    instructorProfile.getGender(),
                    birthDate == null ? null : birthDate.getYear(),
                    instructorProfile.getLevel()
            );
        }
    }
}
