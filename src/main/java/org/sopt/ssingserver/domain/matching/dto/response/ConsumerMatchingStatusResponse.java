package org.sopt.ssingserver.domain.matching.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
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
        Long matchingRequestId,
        MatchingStatus matchingStatus,
        MatchingRequestStatus requestStatus,
        MatchingRequestStatusReason requestStatusReason,
        Long groupId,
        MatchingRequestGroupStatus groupStatus,
        MatchingRequestGroupItemStatus itemStatus,
        MatchingOfferStatus offerStatus,
        MatchingRequestPaymentStatus paymentStatus,
        Instant expiresAt,
        InstructorProfileResponse instructorProfile,
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
    public record InstructorProfileResponse(
            Long instructorId,
            String name,
            String profileImageUrl,
            Gender gender,
            Integer birthYear,
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
