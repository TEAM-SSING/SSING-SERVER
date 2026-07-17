package org.sopt.ssingserver.domain.matching.dto.result;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.instructor.enums.InstructorCertificateType;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestParticipant;
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
        MatchingPriceSummaryResult priceSummary,
        RequestSummaryResult requestSummary,
        LessonSummaryResult lessonSummary
) {

    public MatchingStatusQueryResult(
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
        this(
                matchingRequestId,
                matchingStatus,
                requestStatus,
                requestStatusReason,
                groupId,
                groupStatus,
                itemStatus,
                offerStatus,
                paymentStatus,
                progressSummary,
                expiresAt,
                instructorProfile,
                lessonId,
                priceSummary,
                null,
                null
        );
    }

    public record RequestSummaryResult(
            ResortResult resort,
            Sport sport,
            LessonLevel lessonLevel,
            int headcount,
            String requesterName,
            List<ParticipantResult> participants
    ) {

        public static RequestSummaryResult from(
                MatchingRequest matchingRequest,
                List<MatchingRequestParticipant> participants
        ) {
            return new RequestSummaryResult(
                    new ResortResult(
                            matchingRequest.getResort().getCode(),
                            matchingRequest.getResort().getDisplayName()
                    ),
                    matchingRequest.getSport(),
                    matchingRequest.getLessonLevel(),
                    matchingRequest.getHeadcount(),
                    matchingRequest.getMember().getNickname(),
                    participants.stream()
                            .map(ParticipantResult::from)
                            .toList()
            );
        }
    }

    public record ParticipantResult(
            String name,
            int age,
            Gender gender
    ) {

        private static ParticipantResult from(MatchingRequestParticipant participant) {
            return new ParticipantResult(
                    participant.getName(),
                    participant.getAge(),
                    participant.getGender()
            );
        }
    }

    public record ResortResult(
            String code,
            String displayName
    ) {
    }

    public record LessonSummaryResult(
            int durationMinutes,
            int totalHeadcount,
            String startType
    ) {
    }

    public record InstructorProfileResult(
            Long instructorId,
            String name,
            String profileImageUrl,
            Gender gender,
            Integer birthYear,
            Integer level,
            Integer careerYears,
            Long completedLessonCount,
            Double averageRating,
            String introduction,
            List<InstructorCertificateType> certificateTypes,
            LatestReviewResult latestReview
    ) {

        public InstructorProfileResult(
                Long instructorId,
                String name,
                String profileImageUrl,
                Gender gender,
                Integer birthYear,
                Integer level
        ) {
            this(
                    instructorId,
                    name,
                    profileImageUrl,
                    gender,
                    birthYear,
                    level,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        public static InstructorProfileResult from(InstructorProfile instructorProfile) {
            return basicFrom(instructorProfile);
        }

        public static InstructorProfileResult basicFrom(InstructorProfile instructorProfile) {
            Member member = instructorProfile.getMember();
            LocalDate birthDate = instructorProfile.getBirthDate();
            return new InstructorProfileResult(
                    instructorProfile.getId(),
                    instructorProfile.getRealName(),
                    member == null ? null : member.getProfileImageUrl(),
                    instructorProfile.getGender(),
                    birthDate == null ? null : birthDate.getYear(),
                    instructorProfile.getLevel(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        public static InstructorProfileResult activeFrom(
                InstructorProfile instructorProfile,
                int careerYears,
                long completedLessonCount,
                Double averageRating,
                List<InstructorCertificateType> certificateTypes,
                LatestReviewResult latestReview
        ) {
            Member member = instructorProfile.getMember();
            LocalDate birthDate = instructorProfile.getBirthDate();
            return new InstructorProfileResult(
                    instructorProfile.getId(),
                    instructorProfile.getRealName(),
                    member == null ? null : member.getProfileImageUrl(),
                    instructorProfile.getGender(),
                    birthDate == null ? null : birthDate.getYear(),
                    instructorProfile.getLevel(),
                    careerYears,
                    completedLessonCount,
                    averageRating,
                    instructorProfile.getIntro(),
                    certificateTypes,
                    latestReview
            );
        }
    }

    public record LatestReviewResult(
            String content
    ) {
    }
}
