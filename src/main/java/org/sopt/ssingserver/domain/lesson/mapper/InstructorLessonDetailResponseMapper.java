package org.sopt.ssingserver.domain.lesson.mapper;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.lesson.dto.response.InstructorLessonDetailResponse;
import org.sopt.ssingserver.domain.lesson.entity.Lesson;
import org.sopt.ssingserver.domain.lesson.entity.LessonCancellation;
import org.sopt.ssingserver.domain.lesson.entity.LessonParticipant;
import org.sopt.ssingserver.domain.lesson.entity.LessonStartConfirmation;
import org.sopt.ssingserver.domain.lesson.enums.LessonCancellationActor;
import org.sopt.ssingserver.domain.lesson.enums.LessonStartConfirmationActor;
import org.sopt.ssingserver.domain.lesson.enums.LessonStartConfirmationStatus;
import org.sopt.ssingserver.domain.lesson.error.LessonErrorCode;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.time.AppZoneId;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InstructorLessonDetailResponseMapper {

    private final Clock clock;

    public InstructorLessonDetailResponse toResponse(
            Lesson lesson,
            List<LessonParticipant> participants,
            Map<Long, Integer> teamPricesByMatchingRequestId,
            List<LessonStartConfirmation> confirmations,
            Optional<LessonCancellation> latestCancellation
    ) {
        return switch (lesson.getStatus()) {
            case CONFIRMED -> confirmedResponse(lesson, participants, teamPricesByMatchingRequestId, confirmations);
            case IN_PROGRESS -> inProgressResponse(lesson, participants, teamPricesByMatchingRequestId);
            case COMPLETED -> completedResponse(lesson, participants, teamPricesByMatchingRequestId);
            case CANCELED -> canceledResponse(lesson, participants, teamPricesByMatchingRequestId, latestCancellation);
        };
    }

    private InstructorLessonDetailResponse confirmedResponse(
            Lesson lesson,
            List<LessonParticipant> participants,
            Map<Long, Integer> teamPricesByMatchingRequestId,
            List<LessonStartConfirmation> confirmations
    ) {
        // 시작 전 화면은 강사와 팀 단위 준비 완료 상태를 함께 내려줌
        Map<Long, LessonStartConfirmation> confirmedByMatchingRequestId = confirmedConsumerConfirmationsByRequestId(
                confirmations
        );
        boolean instructorConfirmed = confirmations.stream().anyMatch(this::isConfirmedInstructor);
        InstructorLessonDetailResponse.ConfirmedStatusInfoResponse statusInfo =
                InstructorLessonDetailResponse.ConfirmedStatusInfoResponse.of(
                        confirmedConsumerHeadcount(confirmedByMatchingRequestId)
                                + (instructorConfirmed ? 1 : 0),
                        lesson.getTotalHeadcount() + 1,
                        instructorConfirmed,
                        instructorConfirmed
                );

        return InstructorLessonDetailResponse.confirmed(
                lesson.getId(),
                statusInfo,
                activeLessonInfo(lesson, participants, teamPricesByMatchingRequestId),
                confirmedMatchingRequests(participants, confirmedByMatchingRequestId, teamPricesByMatchingRequestId)
        );
    }

    private InstructorLessonDetailResponse inProgressResponse(
            Lesson lesson,
            List<LessonParticipant> participants,
            Map<Long, Integer> teamPricesByMatchingRequestId
    ) {
        // 강습 진행 중에는 실제 시작 시각과 서버 현재 시각으로 타이머 값을 계산
        Instant startedAt = requireInstant(lesson.getStartedAt());
        Instant serverTime = clock.instant();
        Instant expectedEndedAt = startedAt.plus(lesson.getDurationMinutes(), ChronoUnit.MINUTES);

        InstructorLessonDetailResponse.InProgressStatusInfoResponse statusInfo =
                InstructorLessonDetailResponse.InProgressStatusInfoResponse.of(
                        toOffsetDateTime(serverTime),
                        toOffsetDateTime(startedAt),
                        toOffsetDateTime(expectedEndedAt),
                        Math.max(0, ChronoUnit.SECONDS.between(startedAt, serverTime)),
                        Math.max(0, ChronoUnit.SECONDS.between(serverTime, expectedEndedAt))
                );

        return InstructorLessonDetailResponse.inProgress(
                lesson.getId(),
                statusInfo,
                activeLessonInfo(lesson, participants, teamPricesByMatchingRequestId),
                matchingRequests(participants, teamPricesByMatchingRequestId)
        );
    }

    private InstructorLessonDetailResponse completedResponse(
            Lesson lesson,
            List<LessonParticipant> participants,
            Map<Long, Integer> teamPricesByMatchingRequestId
    ) {
        // 강습 종료 후에는 실제 시작/종료 시각 기준의 기록 정보를 내려줌
        Instant startedAt = requireInstant(lesson.getStartedAt());
        Instant completedAt = requireInstant(lesson.getCompletedAt());
        int actualDurationMinutes = Math.toIntExact(Math.max(0, ChronoUnit.MINUTES.between(startedAt, completedAt)));

        return InstructorLessonDetailResponse.completed(
                lesson.getId(),
                InstructorLessonDetailResponse.CompletedLessonInfoResponse.of(
                        representativeConsumerNames(participants),
                        lesson.getTotalHeadcount(),
                        resort(lesson.getResort()),
                        lesson.getSport(),
                        lesson.getLessonLevel(),
                        lesson.getDurationMinutes(),
                        toOffsetDateTime(startedAt),
                        toOffsetDateTime(completedAt),
                        actualDurationMinutes,
                        totalLessonPrice(participants, teamPricesByMatchingRequestId)
                ),
                matchingRequests(participants, teamPricesByMatchingRequestId)
        );
    }

    private InstructorLessonDetailResponse canceledResponse(
            Lesson lesson,
            List<LessonParticipant> participants,
            Map<Long, Integer> teamPricesByMatchingRequestId,
            Optional<LessonCancellation> latestCancellation
    ) {
        // lesson이 취소 상태이면 가장 마지막 취소 이력을 화면 복구 기준으로 사용
        LessonCancellation cancellation = latestCancellation
                .orElseThrow(() -> new BusinessException(LessonErrorCode.LESSON_CANCELLATION_NOT_FOUND));

        return InstructorLessonDetailResponse.canceled(
                lesson.getId(),
                InstructorLessonDetailResponse.CancelInfoResponse.of(
                        toOffsetDateTime(cancellation.getCanceledAt()),
                        canceledBy(lesson, cancellation),
                        cancellation.getCancelReason()
                ),
                InstructorLessonDetailResponse.CanceledLessonInfoResponse.of(
                        representativeConsumerNames(participants),
                        lesson.getTotalHeadcount(),
                        resort(lesson.getResort()),
                        lesson.getSport(),
                        lesson.getLessonLevel(),
                        lesson.getDurationMinutes(),
                        totalLessonPrice(participants, teamPricesByMatchingRequestId)
                ),
                matchingRequests(participants, teamPricesByMatchingRequestId)
        );
    }

    private InstructorLessonDetailResponse.LessonInfoResponse activeLessonInfo(
            Lesson lesson,
            List<LessonParticipant> participants,
            Map<Long, Integer> teamPricesByMatchingRequestId
    ) {
        return InstructorLessonDetailResponse.LessonInfoResponse.of(
                representativeConsumerNames(participants),
                lesson.getTotalHeadcount(),
                resort(lesson.getResort()),
                lesson.getSport(),
                lesson.getLessonLevel(),
                toOffsetDateTime(lesson.getScheduledAt()),
                lesson.getDurationMinutes(),
                totalLessonPrice(participants, teamPricesByMatchingRequestId)
        );
    }

    private List<InstructorLessonDetailResponse.ConfirmedMatchingRequestResponse> confirmedMatchingRequests(
            List<LessonParticipant> participants,
            Map<Long, LessonStartConfirmation> confirmedByMatchingRequestId,
            Map<Long, Integer> teamPricesByMatchingRequestId
    ) {
        return groupedParticipants(participants)
                .values()
                .stream()
                .map(group -> confirmedMatchingRequest(group, confirmedByMatchingRequestId, teamPricesByMatchingRequestId))
                .toList();
    }

    private InstructorLessonDetailResponse.ConfirmedMatchingRequestResponse confirmedMatchingRequest(
            List<LessonParticipant> participants,
            Map<Long, LessonStartConfirmation> confirmedByMatchingRequestId,
            Map<Long, Integer> teamPricesByMatchingRequestId
    ) {
        LessonParticipant firstParticipant = firstParticipant(participants);
        MatchingRequest matchingRequest = firstParticipant.getMatchingRequest();
        Member representativeMember = matchingRequest.getMember();
        List<InstructorLessonDetailResponse.ParticipantResponse> participantResponses = participants.stream()
                .map(participant -> InstructorLessonDetailResponse.ParticipantResponse.of(
                        participant.getId(),
                        participant.getGender(),
                        participant.getAge()
                ))
                .toList();

        return InstructorLessonDetailResponse.ConfirmedMatchingRequestResponse.of(
                matchingRequest.getId(),
                representativeMember.getId(),
                representativeMember.getNickname(),
                matchingRequest.getHeadcount(),
                teamLessonPrice(matchingRequest.getId(), teamPricesByMatchingRequestId),
                confirmedByMatchingRequestId.containsKey(matchingRequest.getId()),
                participantResponses
        );
    }

    private List<InstructorLessonDetailResponse.MatchingRequestResponse> matchingRequests(
            List<LessonParticipant> participants,
            Map<Long, Integer> teamPricesByMatchingRequestId
    ) {
        return groupedParticipants(participants)
                .values()
                .stream()
                .map(group -> matchingRequest(group, teamPricesByMatchingRequestId))
                .toList();
    }

    private InstructorLessonDetailResponse.MatchingRequestResponse matchingRequest(
            List<LessonParticipant> participants,
            Map<Long, Integer> teamPricesByMatchingRequestId
    ) {
        LessonParticipant firstParticipant = firstParticipant(participants);
        MatchingRequest matchingRequest = firstParticipant.getMatchingRequest();
        Member representativeMember = matchingRequest.getMember();
        List<InstructorLessonDetailResponse.ParticipantResponse> participantResponses = participants.stream()
                .map(participant -> InstructorLessonDetailResponse.ParticipantResponse.of(
                        participant.getId(),
                        participant.getGender(),
                        participant.getAge()
                ))
                .toList();

        return InstructorLessonDetailResponse.MatchingRequestResponse.of(
                matchingRequest.getId(),
                representativeMember.getId(),
                representativeMember.getNickname(),
                matchingRequest.getHeadcount(),
                teamLessonPrice(matchingRequest.getId(), teamPricesByMatchingRequestId),
                participantResponses
        );
    }

    private Map<Long, List<LessonParticipant>> groupedParticipants(List<LessonParticipant> participants) {
        return participants.stream()
                .collect(Collectors.groupingBy(
                        participant -> participant.getMatchingRequest().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private List<String> representativeConsumerNames(List<LessonParticipant> participants) {
        return groupedParticipants(participants)
                .values()
                .stream()
                .map(group -> firstParticipant(group).getMatchingRequest().getMember().getNickname())
                .toList();
    }

    private Map<Long, LessonStartConfirmation> confirmedConsumerConfirmationsByRequestId(
            List<LessonStartConfirmation> confirmations
    ) {
        Map<Long, LessonStartConfirmation> confirmedByMatchingRequestId = new LinkedHashMap<>();
        for (LessonStartConfirmation confirmation : confirmations) {
            if (isConfirmedConsumer(confirmation) && confirmation.getMatchingRequest() != null) {
                confirmedByMatchingRequestId.put(confirmation.getMatchingRequest().getId(), confirmation);
            }
        }
        return confirmedByMatchingRequestId;
    }

    private int confirmedConsumerHeadcount(
            Map<Long, LessonStartConfirmation> confirmedByMatchingRequestId
    ) {
        return confirmedByMatchingRequestId.values().stream()
                .map(LessonStartConfirmation::getMatchingRequest)
                .mapToInt(MatchingRequest::getHeadcount)
                .sum();
    }

    private boolean isConfirmedInstructor(LessonStartConfirmation confirmation) {
        return confirmation.getActorType() == LessonStartConfirmationActor.INSTRUCTOR
                && confirmation.getStatus() == LessonStartConfirmationStatus.CONFIRMED;
    }

    private boolean isConfirmedConsumer(LessonStartConfirmation confirmation) {
        return confirmation.getActorType() == LessonStartConfirmationActor.CONSUMER
                && confirmation.getStatus() == LessonStartConfirmationStatus.CONFIRMED;
    }

    private InstructorLessonDetailResponse.CanceledByResponse canceledBy(
            Lesson lesson,
            LessonCancellation cancellation
    ) {
        Member member = cancellation.getMember();
        String name = cancellation.getCanceledBy() == LessonCancellationActor.INSTRUCTOR
                ? lesson.getInstructorProfile().getRealName()
                : member.getNickname();
        return InstructorLessonDetailResponse.CanceledByResponse.of(member.getId(), name);
    }

    private int totalLessonPrice(
            List<LessonParticipant> participants,
            Map<Long, Integer> teamPricesByMatchingRequestId
    ) {
        // 전체 강습 가격은 팀별 결제 요청 금액을 합산해서 계산
        return groupedParticipants(participants)
                .keySet()
                .stream()
                .mapToInt(matchingRequestId -> teamLessonPrice(matchingRequestId, teamPricesByMatchingRequestId))
                .sum();
    }

    private int teamLessonPrice(
            Long matchingRequestId,
            Map<Long, Integer> teamPricesByMatchingRequestId
    ) {
        Integer price = teamPricesByMatchingRequestId.get(matchingRequestId);
        if (price == null) {
            throw new BusinessException(LessonErrorCode.LESSON_PRICE_NOT_FOUND);
        }
        return price;
    }

    private LessonParticipant firstParticipant(List<LessonParticipant> participants) {
        return participants.stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(LessonErrorCode.LESSON_INVALID_STATE));
    }

    private InstructorLessonDetailResponse.ResortResponse resort(Resort resort) {
        return InstructorLessonDetailResponse.ResortResponse.of(resort.getCode(), resort.getDisplayName());
    }

    private Instant requireInstant(Instant instant) {
        if (instant == null) {
            throw new BusinessException(LessonErrorCode.LESSON_INVALID_STATE);
        }
        return instant;
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atZone(AppZoneId.SEOUL).toOffsetDateTime();
    }
}
