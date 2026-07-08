package org.sopt.ssingserver.domain.matching.dto.result;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.lesson.entity.Lesson;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroup;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroupItem;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupItemStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.Gender;
import org.sopt.ssingserver.domain.payment.entity.MatchingRequestPayment;
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
        Instant expiresAt,
        InstructorProfileResult instructorProfile,
        Long lessonId
) {

    public static MatchingStatusQueryResult of(
            MatchingRequest matchingRequest,
            MatchingStatus matchingStatus,
            Optional<MatchingRequestGroupItem> matchingRequestGroupItem,
            Optional<MatchingOffer> matchingOffer,
            Optional<MatchingRequestPayment> matchingRequestPayment,
            Optional<Lesson> lesson
    ) {
        Optional<MatchingRequestGroup> matchingRequestGroup = matchingRequestGroupItem
                .map(MatchingRequestGroupItem::getMatchingRequestGroup)
                .or(() -> matchingOffer.map(MatchingOffer::getMatchingRequestGroup));

        return new MatchingStatusQueryResult(
                matchingRequest.getId(),
                matchingStatus,
                matchingRequest.getStatus(),
                matchingRequest.getStatusReason(),
                matchingRequestGroup.map(MatchingRequestGroup::getId).orElse(null),
                matchingRequestGroup.map(MatchingRequestGroup::getStatus).orElse(null),
                matchingRequestGroupItem.map(MatchingRequestGroupItem::getStatus).orElse(null),
                matchingOffer.map(MatchingOffer::getStatus).orElse(null),
                matchingRequestPayment.map(MatchingRequestPayment::getStatus).orElse(null),
                resolveExpiresAt(matchingStatus, matchingRequest, matchingRequestPayment),
                matchingOffer
                        .map(MatchingOffer::getInstructorProfile)
                        .map(InstructorProfileResult::from)
                        .orElse(null),
                resolveLessonId(matchingStatus, lesson)
        );
    }

    private static Instant resolveExpiresAt(
            MatchingStatus matchingStatus,
            MatchingRequest matchingRequest,
            Optional<MatchingRequestPayment> matchingRequestPayment
    ) {
        // 현재 앱 표시 상태에 맞는 다음 전환 기준 시각만 선택
        return switch (matchingStatus) {
            case SEARCHING,
                 WAITING_FOR_TEAM,
                 WAITING_FOR_INSTRUCTOR,
                 WAITING_FOR_CONFIRMATION,
                 WAITING_FOR_OTHER_CONFIRMATIONS -> matchingRequest.getExpiresAt();
            case PAYMENT_PENDING,
                 WAITING_FOR_OTHER_PAYMENTS,
                 PAYMENT_EXPIRED -> matchingRequestPayment
                    .map(MatchingRequestPayment::getPaymentExpiresAt)
                    .orElse(null);
            case CONFIRMED,
                 NO_AVAILABLE_INSTRUCTOR,
                 REMATCHING,
                 CANCELED,
                 FAILED -> null;
        };
    }

    private static Long resolveLessonId(
            MatchingStatus matchingStatus,
            Optional<Lesson> lesson
    ) {
        // 확정 매칭 화면 복구에만 필요한 강습 ID 노출
        if (matchingStatus != MatchingStatus.CONFIRMED) {
            return null;
        }

        return lesson.map(Lesson::getId).orElse(null);
    }

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
                    // 현재 강사 등급 원천 컬럼 부재로 명세 확정 전까지 응답 DTO에서 생략되는 값
                    null
            );
        }
    }
}
