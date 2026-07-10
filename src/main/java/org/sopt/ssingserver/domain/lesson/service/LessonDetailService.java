package org.sopt.ssingserver.domain.lesson.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.lesson.dto.response.ConsumerLessonDetailResponse;
import org.sopt.ssingserver.domain.lesson.dto.response.InstructorLessonDetailResponse;
import org.sopt.ssingserver.domain.lesson.entity.Lesson;
import org.sopt.ssingserver.domain.lesson.entity.LessonCancellation;
import org.sopt.ssingserver.domain.lesson.entity.LessonParticipant;
import org.sopt.ssingserver.domain.lesson.entity.LessonStartConfirmation;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;
import org.sopt.ssingserver.domain.lesson.error.LessonErrorCode;
import org.sopt.ssingserver.domain.lesson.mapper.ConsumerLessonDetailResponseMapper;
import org.sopt.ssingserver.domain.lesson.mapper.InstructorLessonDetailResponseMapper;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LessonDetailService {

    private final LessonDetailReader lessonDetailReader;
    private final ConsumerLessonDetailResponseMapper consumerResponseMapper;
    private final InstructorLessonDetailResponseMapper instructorResponseMapper;

    @Transactional(readOnly = true)
    public ConsumerLessonDetailResponse getConsumerDetail(Long memberId, Long lessonId) {
        // 강습 기본 정보 조회
        Lesson lesson = lessonDetailReader.getLesson(lessonId);
        List<LessonParticipant> participants = lessonDetailReader.getParticipants(lessonId);

        // 강습에 참여한 모든 강습생 정보를 조회
        List<Long> matchingRequestIds = lessonDetailReader.getMatchingRequestIdsByLessonIdAndMemberId(
                lessonId, memberId
        );
        if (matchingRequestIds.isEmpty()) {
            throw new BusinessException(LessonErrorCode.LESSON_FORBIDDEN);
        }
        Long myMatchingRequestId = matchingRequestIds.get(0);

        // 내 팀 가격 조회
        int myTeamLessonPrice = lessonDetailReader.getTeamLessonPrice(
                myMatchingRequestId,
                lesson.getMatchingOffer().getId()
        );

        // 내 팀이 이 강습을 취소했는지 조회
        Optional<LessonCancellation> myCancellation = lessonDetailReader.getLatestCancellationByMatchingRequestId(
                lessonId,
                myMatchingRequestId
        );
        LessonStatus responseStatus = myCancellation.isPresent() ? LessonStatus.CANCELED : lesson.getStatus();

        // 시작 전 상태에서만 강사와 각 팀의 준비 완료 이력을 조회
        List<LessonStartConfirmation> confirmations = lessonDetailReader.getConfirmationsIfConfirmed(
                lessonId,
                responseStatus
        );

        // 취소 상태에서만 강사 취소 이력을 조회
        Optional<LessonCancellation> instructorCancellation = lessonDetailReader.getLatestInstructorCancellationIfCanceled(
                lessonId,
                responseStatus
        );

        return consumerResponseMapper.toResponse(
                lesson,
                participants,
                myMatchingRequestId,
                myTeamLessonPrice,
                confirmations,
                myCancellation,
                instructorCancellation
        );
    }

    @Transactional(readOnly = true)
    public InstructorLessonDetailResponse getInstructorDetail(
            Long memberId,
            Long lessonId
    ) {
        // 강습을 담당하는 강사 본인만 상세 정보를 조회할 수 있음
        Lesson lesson = lessonDetailReader.getLesson(lessonId);
        if (!lesson.getInstructorProfile().getMember().getId().equals(memberId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        // 강사 화면은 모든 팀 카드가 필요하므로 전체 참여자와 팀별 결제 금액을 함께 조회
        List<LessonParticipant> participants = lessonDetailReader.getParticipants(lessonId);
        Map<Long, Integer> teamPricesByMatchingRequestId = lessonDetailReader.getTeamLessonPricesByMatchingOfferId(
                lesson.getMatchingOffer().getId()
        );
        // 시작 전 상태에서만 강사와 각 팀의 준비 완료 이력을 조회
        List<LessonStartConfirmation> confirmations = lessonDetailReader.getConfirmationsIfConfirmed(
                lessonId,
                lesson.getStatus()
        );
        // 강사 앱은 lesson 자체가 취소 상태일 때만 최신 취소 정보를 보여줌
        Optional<LessonCancellation> latestCancellation = lessonDetailReader.getLatestCancellationIfCanceled(
                lessonId,
                lesson.getStatus()
        );

        return instructorResponseMapper.toResponse(
                lesson,
                participants,
                teamPricesByMatchingRequestId,
                confirmations,
                latestCancellation
        );
    }
}
