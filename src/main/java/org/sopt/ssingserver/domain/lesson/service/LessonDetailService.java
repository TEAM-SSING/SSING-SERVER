package org.sopt.ssingserver.domain.lesson.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.lesson.dto.response.ConsumerLessonDetailResponse;
import org.sopt.ssingserver.domain.lesson.entity.Lesson;
import org.sopt.ssingserver.domain.lesson.entity.LessonCancellation;
import org.sopt.ssingserver.domain.lesson.entity.LessonParticipant;
import org.sopt.ssingserver.domain.lesson.error.LessonErrorCode;
import org.sopt.ssingserver.domain.lesson.mapper.ConsumerLessonDetailResponseMapper;
import org.sopt.ssingserver.domain.lesson.repository.LessonCancellationRepository;
import org.sopt.ssingserver.domain.lesson.repository.LessonParticipantRepository;
import org.sopt.ssingserver.domain.lesson.repository.LessonRepository;
import org.sopt.ssingserver.domain.payment.entity.MatchingRequestPayment;
import org.sopt.ssingserver.domain.payment.repository.MatchingRequestPaymentRepository;
import org.sopt.ssingserver.global.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LessonDetailService {

    private final LessonRepository lessonRepository;
    private final LessonParticipantRepository lessonParticipantRepository;
    private final LessonCancellationRepository lessonCancellationRepository;
    private final MatchingRequestPaymentRepository matchingRequestPaymentRepository;
    private final ConsumerLessonDetailResponseMapper responseMapper;

    @Transactional(readOnly = true)
    public ConsumerLessonDetailResponse getDetail(Long memberId, Long lessonId) {
        // 강습 기본 정보 조회
        Lesson lesson = lessonRepository.findWithDetailById(lessonId)
                .orElseThrow(() -> new BusinessException(LessonErrorCode.LESSON_NOT_FOUND));
        List<LessonParticipant> participants = lessonParticipantRepository.findDetailParticipantsByLessonId(lessonId);

        // 강습에 참여한 모든 강습생 정보를 조회
        List<Long> matchingRequestIds = lessonParticipantRepository.findMatchingRequestIdsByLessonIdAndMemberId(
                lessonId, memberId
        );
        if (matchingRequestIds.isEmpty()) {
            throw new BusinessException(LessonErrorCode.LESSON_FORBIDDEN);
        }
        Long myMatchingRequestId = matchingRequestIds.get(0);

        // 내 팀 가격 조회
        int myTeamLessonPrice = matchingRequestPaymentRepository
                .findFirstByMatchingRequestIdAndMatchingOfferIdOrderByIdDesc(
                        myMatchingRequestId, lesson.getMatchingOffer().getId())
                .map(MatchingRequestPayment::getAmount)
                .orElseThrow(() -> new BusinessException(LessonErrorCode.LESSON_PRICE_NOT_FOUND));

        // 내 팀이 이 강습을 취소했는지 조회
        Optional<LessonCancellation> myCancellation = lessonCancellationRepository
                .findByLessonIdAndMatchingRequestId(lessonId, myMatchingRequestId)
                .stream()
                .max(Comparator.comparing(LessonCancellation::getCanceledAt));

        return responseMapper.toResponse(
                lesson,
                participants,
                myMatchingRequestId,
                myTeamLessonPrice,
                myCancellation
        );
    }
}
