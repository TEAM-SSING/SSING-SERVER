package org.sopt.ssingserver.domain.matching.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.lesson.entity.Lesson;
import org.sopt.ssingserver.domain.lesson.repository.LessonRepository;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingStatusQueryResult;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroup;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroupItem;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.matching.error.MatchingErrorCode;
import org.sopt.ssingserver.domain.matching.repository.MatchingOfferRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestGroupItemRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestRepository;
import org.sopt.ssingserver.domain.payment.entity.MatchingRequestPayment;
import org.sopt.ssingserver.domain.payment.repository.MatchingRequestPaymentRepository;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MatchingStatusQueryService {

    // 매칭 시간 정책의 MVP 기준: 강사 제안 응답 제한 시간 1분
    private static final Duration INSTRUCTOR_RESPONSE_TIMEOUT = Duration.ofMinutes(1);

    private final MatchingRequestRepository matchingRequestRepository;
    private final MatchingRequestGroupItemRepository matchingRequestGroupItemRepository;
    private final MatchingOfferRepository matchingOfferRepository;
    private final MatchingRequestPaymentRepository matchingRequestPaymentRepository;
    private final LessonRepository lessonRepository;
    private final MatchingStatusResolver matchingStatusResolver;

    @Transactional(readOnly = true)
    public MatchingStatusQueryResult getStatus(
            Long memberId,
            Long matchingRequestId
    ) {
        MatchingRequest matchingRequest = matchingRequestRepository.findById(matchingRequestId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.MATCHING_REQUEST_NOT_FOUND));

        validateOwner(memberId, matchingRequest);

        // 조회 API는 상태를 바꾸지 않고 현재 요청 주변 row를 모아 Android 복구 응답을 만든다.
        Optional<MatchingRequestGroupItem> matchingRequestGroupItem =
                matchingRequestGroupItemRepository.findFirstByMatchingRequestIdOrderByIdDesc(matchingRequestId);
        Optional<MatchingRequestGroup> matchingRequestGroup = matchingRequestGroupItem
                .map(MatchingRequestGroupItem::getMatchingRequestGroup);
        Optional<MatchingOffer> matchingOffer = findCurrentOffer(matchingRequest, matchingRequestGroup);
        Optional<MatchingRequestPayment> matchingRequestPayment =
                matchingRequestPaymentRepository.findFirstByMatchingRequestIdOrderByIdDesc(matchingRequestId);

        MatchingStatus matchingStatus = matchingStatusResolver.resolve(
                matchingRequest,
                matchingRequestGroup,
                matchingRequestGroupItem,
                matchingOffer,
                matchingRequestPayment
        );
        Optional<Lesson> lesson = findConfirmedLesson(matchingStatus, matchingOffer);

        return toQueryResult(
                matchingRequest,
                matchingStatus,
                matchingRequestGroup,
                matchingRequestGroupItem,
                matchingOffer,
                matchingRequestPayment,
                lesson
        );
    }

    private static MatchingStatusQueryResult toQueryResult(
            MatchingRequest matchingRequest,
            MatchingStatus matchingStatus,
            Optional<MatchingRequestGroup> matchingRequestGroup,
            Optional<MatchingRequestGroupItem> matchingRequestGroupItem,
            Optional<MatchingOffer> matchingOffer,
            Optional<MatchingRequestPayment> matchingRequestPayment,
            Optional<Lesson> lesson
    ) {
        Optional<MatchingRequestGroup> responseMatchingRequestGroup = matchingRequestGroup
                .or(() -> matchingOffer.map(MatchingOffer::getMatchingRequestGroup));

        return new MatchingStatusQueryResult(
                matchingRequest.getId(),
                matchingStatus,
                matchingRequest.getStatus(),
                matchingRequest.getStatusReason(),
                responseMatchingRequestGroup.map(MatchingRequestGroup::getId).orElse(null),
                responseMatchingRequestGroup.map(MatchingRequestGroup::getStatus).orElse(null),
                matchingRequestGroupItem.map(MatchingRequestGroupItem::getStatus).orElse(null),
                matchingOffer.map(MatchingOffer::getStatus).orElse(null),
                matchingRequestPayment.map(MatchingRequestPayment::getStatus).orElse(null),
                resolveExpiresAt(matchingStatus, matchingRequest, matchingOffer, matchingRequestPayment),
                resolveInstructorProfile(matchingOffer),
                resolveLessonId(matchingStatus, lesson)
        );
    }

    private static Instant resolveExpiresAt(
            MatchingStatus matchingStatus,
            MatchingRequest matchingRequest,
            Optional<MatchingOffer> matchingOffer,
            Optional<MatchingRequestPayment> matchingRequestPayment
    ) {
        // 현재 앱 표시 상태에 맞는 다음 전환 기준 시각만 선택
        return switch (matchingStatus) {
            case SEARCHING,
                 WAITING_FOR_TEAM -> matchingRequest.getExpiresAt();
            case WAITING_FOR_INSTRUCTOR -> matchingOffer
                    .map(MatchingStatusQueryService::resolveInstructorResponseExpiresAt)
                    .orElse(null);
            // TODO: 강습생 응답 타임아웃 정책이 MatchingRequest.expiresAt과 분리되면 별도 계산 기준 추가
            case WAITING_FOR_CONFIRMATION,
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

    private static Instant resolveInstructorResponseExpiresAt(MatchingOffer matchingOffer) {
        return matchingOffer.getExposedAt().plus(INSTRUCTOR_RESPONSE_TIMEOUT);
    }

    private static MatchingStatusQueryResult.InstructorProfileResult resolveInstructorProfile(
            Optional<MatchingOffer> matchingOffer
    ) {
        return matchingOffer
                .filter(offer -> offer.getStatus() == MatchingOfferStatus.ACCEPTED)
                .map(MatchingOffer::getInstructorProfile)
                .map(MatchingStatusQueryResult.InstructorProfileResult::from)
                .orElse(null);
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

    private void validateOwner(
            Long memberId,
            MatchingRequest matchingRequest
    ) {
        Long ownerId = matchingRequest.getMember().getId();
        // 컨트롤러 권한 통과 이후에도 요청 소유자만 조회 가능한 도메인 보안 경계
        if (!Objects.equals(ownerId, memberId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }

    private Optional<MatchingOffer> findCurrentOffer(
            MatchingRequest matchingRequest,
            Optional<MatchingRequestGroup> matchingRequestGroup
    ) {
        if (matchingRequest.getMatchingOffer() != null) {
            return Optional.of(matchingRequest.getMatchingOffer());
        }

        return matchingRequestGroup
                .map(MatchingRequestGroup::getId)
                .filter(Objects::nonNull)
                .flatMap(matchingOfferRepository::findFirstByMatchingRequestGroupIdOrderByIdDesc);
    }

    private Optional<Lesson> findConfirmedLesson(
            MatchingStatus matchingStatus,
            Optional<MatchingOffer> matchingOffer
    ) {
        // 확정 상태에서만 강습 ID를 노출하는 API 계약 보호
        if (matchingStatus != MatchingStatus.CONFIRMED) {
            return Optional.empty();
        }

        return matchingOffer
                .map(MatchingOffer::getId)
                .filter(Objects::nonNull)
                .flatMap(lessonRepository::findByMatchingOfferId);
    }
}
