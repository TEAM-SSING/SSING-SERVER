package org.sopt.ssingserver.domain.lesson.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.lesson.dto.result.LessonPriceSummaryResult;
import org.sopt.ssingserver.domain.lesson.entity.Lesson;
import org.sopt.ssingserver.domain.lesson.entity.LessonCancellation;
import org.sopt.ssingserver.domain.lesson.entity.LessonParticipant;
import org.sopt.ssingserver.domain.lesson.entity.LessonStartConfirmation;
import org.sopt.ssingserver.domain.lesson.enums.LessonCancellationActor;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;
import org.sopt.ssingserver.domain.lesson.error.LessonErrorCode;
import org.sopt.ssingserver.domain.lesson.repository.LessonCancellationRepository;
import org.sopt.ssingserver.domain.lesson.repository.LessonParticipantRepository;
import org.sopt.ssingserver.domain.lesson.repository.LessonRepository;
import org.sopt.ssingserver.domain.lesson.repository.LessonStartConfirmationRepository;
import org.sopt.ssingserver.domain.payment.entity.MatchingRequestPayment;
import org.sopt.ssingserver.domain.payment.repository.MatchingOfferPriceSnapshotRepository;
import org.sopt.ssingserver.domain.payment.repository.MatchingRequestPaymentRepository;
import org.sopt.ssingserver.global.error.BusinessException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LessonDetailReader {

    private final LessonRepository lessonRepository;
    private final LessonParticipantRepository lessonParticipantRepository;
    private final LessonCancellationRepository lessonCancellationRepository;
    private final LessonStartConfirmationRepository lessonStartConfirmationRepository;
    private final MatchingRequestPaymentRepository matchingRequestPaymentRepository;
    private final MatchingOfferPriceSnapshotRepository matchingOfferPriceSnapshotRepository;

    public Lesson getLesson(Long lessonId) {
        return lessonRepository.findWithDetailById(lessonId)
                .orElseThrow(() -> new BusinessException(LessonErrorCode.LESSON_NOT_FOUND));
    }

    public List<LessonParticipant> getParticipants(Long lessonId) {
        return lessonParticipantRepository.findDetailParticipantsByLessonId(lessonId);
    }

    public List<Long> getMatchingRequestIdsByLessonIdAndMemberId(
            Long lessonId,
            Long memberId
    ) {
        return lessonParticipantRepository.findMatchingRequestIdsByLessonIdAndMemberId(lessonId, memberId);
    }

    public LessonPriceSummaryResult getTeamPriceSummary(
            Long matchingRequestId,
            Long matchingOfferId
    ) {
        return matchingRequestPaymentRepository
                .findByMatchingRequestIdAndMatchingOfferId(matchingRequestId, matchingOfferId)
                .map(MatchingRequestPayment::getMatchingRequestPriceSnapshot)
                .map(LessonPriceSummaryResult::from)
                .orElseThrow(() -> new BusinessException(LessonErrorCode.LESSON_PRICE_NOT_FOUND));
    }

    // 강사 팀 카드에는 패찰비를 제외한 요청별 강습비 snapshot만 사용한다.
    public Map<Long, Integer> getTeamLessonPricesByMatchingOfferId(Long matchingOfferId) {
        Map<Long, Integer> pricesByMatchingRequestId = new LinkedHashMap<>();
        for (MatchingRequestPayment payment : matchingRequestPaymentRepository
                .findByMatchingOfferIdOrderByMatchingRequestIdAsc(matchingOfferId)) {
            pricesByMatchingRequestId.put(
                    payment.getMatchingRequest().getId(),
                    payment.getMatchingRequestPriceSnapshot().getLessonPriceAmount()
            );
        }
        return pricesByMatchingRequestId;
    }

    public int getInstructorSettlementAmount(Long matchingOfferId) {
        return matchingOfferPriceSnapshotRepository.findByMatchingOfferId(matchingOfferId)
                .map(snapshot -> snapshot.getInstructorSettlementAmount())
                .orElseThrow(() -> new BusinessException(LessonErrorCode.LESSON_PRICE_NOT_FOUND));
    }

    public List<LessonStartConfirmation> getConfirmationsIfConfirmed(
            Long lessonId,
            LessonStatus lessonStatus
    ) {
        if (lessonStatus != LessonStatus.CONFIRMED) {
            return List.of();
        }
        return lessonStartConfirmationRepository.findByLessonId(lessonId);
    }

    // 강사 취소 화면은 lesson 전체 취소 이력 중 마지막 취소 사유를 기준으로 복구
    public Optional<LessonCancellation> getLatestCancellationIfCanceled(
            Long lessonId,
            LessonStatus lessonStatus
    ) {
        if (lessonStatus != LessonStatus.CANCELED) {
            return Optional.empty();
        }
        return lessonCancellationRepository.findByLessonId(lessonId)
                .stream()
                .max(Comparator.comparing(LessonCancellation::getCanceledAt));
    }

    public Optional<LessonCancellation> getLatestCancellationByMatchingRequestId(
            Long lessonId,
            Long matchingRequestId
    ) {
        return lessonCancellationRepository.findByLessonIdAndMatchingRequestId(lessonId, matchingRequestId)
                .stream()
                .max(Comparator.comparing(LessonCancellation::getCanceledAt));
    }

    public Optional<LessonCancellation> getLatestInstructorCancellationIfCanceled(
            Long lessonId,
            LessonStatus lessonStatus
    ) {
        if (lessonStatus != LessonStatus.CANCELED) {
            return Optional.empty();
        }
        return lessonCancellationRepository.findByLessonIdAndCanceledBy(lessonId, LessonCancellationActor.INSTRUCTOR)
                .stream()
                .max(Comparator.comparing(LessonCancellation::getCanceledAt));
    }
}
