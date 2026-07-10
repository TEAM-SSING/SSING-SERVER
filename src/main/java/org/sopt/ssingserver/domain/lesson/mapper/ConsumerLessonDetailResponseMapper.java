package org.sopt.ssingserver.domain.lesson.mapper;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.lesson.dto.response.ConsumerLessonDetailResponse;
import org.sopt.ssingserver.domain.lesson.entity.Lesson;
import org.sopt.ssingserver.domain.lesson.entity.LessonCancellation;
import org.sopt.ssingserver.domain.lesson.entity.LessonParticipant;
import org.sopt.ssingserver.domain.lesson.entity.LessonStartConfirmation;
import org.sopt.ssingserver.domain.lesson.enums.LessonCancellationActor;
import org.sopt.ssingserver.domain.lesson.enums.LessonStartConfirmationActor;
import org.sopt.ssingserver.domain.lesson.enums.LessonStartConfirmationStatus;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;
import org.sopt.ssingserver.domain.lesson.error.LessonErrorCode;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.time.AppZoneId;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConsumerLessonDetailResponseMapper {

    private final Clock clock;

    public ConsumerLessonDetailResponse toResponse(
            Lesson lesson,
            List<LessonParticipant> participants,
            Long myMatchingRequestId,
            int myTeamLessonPrice,
            List<LessonStartConfirmation> confirmations,
            Optional<LessonCancellation> myCancellation,
            Optional<LessonCancellation> instructorCancellation
    ) {
        LessonStatus responseStatus = myCancellation.isPresent() ? LessonStatus.CANCELED : lesson.getStatus();
        return switch (responseStatus) {
            case CONFIRMED -> confirmedResponse(
                    lesson, participants, myMatchingRequestId, myTeamLessonPrice, confirmations);
            case IN_PROGRESS -> inProgressResponse(lesson, participants, myTeamLessonPrice);
            case COMPLETED -> completedResponse(lesson, participants, myTeamLessonPrice);
            case CANCELED -> canceledResponse(
                    lesson, participants, myTeamLessonPrice, myCancellation, instructorCancellation);
        };
    }

    private ConsumerLessonDetailResponse confirmedResponse(
            Lesson lesson,
            List<LessonParticipant> participants,
            Long myMatchingRequestId,
            int myTeamLessonPrice,
            List<LessonStartConfirmation> confirmations
    ) {
        // 강습 시작 전 강사와 각 팀이 준비 완료를 눌렀는지 확인
        Map<Long, LessonStartConfirmation> confirmedByMatchingRequestId = confirmedConsumerConfirmationsByRequestId(
                confirmations
        );
        boolean instructorConfirmed = confirmations.stream().anyMatch(this::isConfirmedInstructor);

        ConsumerLessonDetailResponse.ConfirmedStatusInfoResponse statusInfo =
                ConsumerLessonDetailResponse.ConfirmedStatusInfoResponse.of(
                        confirmedByMatchingRequestId.size() + (instructorConfirmed ? 1 : 0),
                        groupedParticipants(participants).size() + 1,
                        confirmedByMatchingRequestId.containsKey(myMatchingRequestId),
                        instructorConfirmed
                );

        return ConsumerLessonDetailResponse.confirmed(
                lesson.getId(),
                statusInfo,
                activeLessonInfo(lesson, participants, myTeamLessonPrice),
                instructorProfile(lesson.getInstructorProfile()),
                confirmedMatchingRequests(participants, confirmedByMatchingRequestId)
        );
    }

    private ConsumerLessonDetailResponse inProgressResponse(
            Lesson lesson,
            List<LessonParticipant> participants,
            int myTeamLessonPrice
    ) {
        // 강습 진행 중에는 실제 시작 시각과 서버 현재 시각으로 타이머 값을 계산
        Instant startedAt = requireInstant(lesson.getStartedAt());
        Instant serverTime = clock.instant();
        Instant expectedEndedAt = startedAt.plus(lesson.getDurationMinutes(), ChronoUnit.MINUTES);

        ConsumerLessonDetailResponse.InProgressStatusInfoResponse statusInfo =
                ConsumerLessonDetailResponse.InProgressStatusInfoResponse.of(
                        toOffsetDateTime(serverTime),
                        toOffsetDateTime(startedAt),
                        toOffsetDateTime(expectedEndedAt),
                        Math.max(0, ChronoUnit.SECONDS.between(startedAt, serverTime)),
                        Math.max(0, ChronoUnit.SECONDS.between(serverTime, expectedEndedAt))
                );

        return ConsumerLessonDetailResponse.inProgress(
                lesson.getId(),
                statusInfo,
                activeLessonInfo(lesson, participants, myTeamLessonPrice),
                instructorProfile(lesson.getInstructorProfile()),
                matchingRequests(participants)
        );
    }

    private ConsumerLessonDetailResponse completedResponse(
            Lesson lesson,
            List<LessonParticipant> participants,
            int myTeamLessonPrice
    ) {
        // 강습 종료 후에는 실제 시작/종료 시각 기준의 기록 정보를 내려줌
        Instant startedAt = requireInstant(lesson.getStartedAt());
        Instant completedAt = requireInstant(lesson.getCompletedAt());
        int actualDurationMinutes = Math.toIntExact(Math.max(0, ChronoUnit.MINUTES.between(startedAt, completedAt)));

        return ConsumerLessonDetailResponse.completed(
                lesson.getId(),
                ConsumerLessonDetailResponse.CompletedLessonInfoResponse.of(
                        representativeConsumerNames(participants),
                        lesson.getTotalHeadcount(),
                        resort(lesson.getResort()),
                        lesson.getSport(),
                        lesson.getLessonLevel(),
                        lesson.getDurationMinutes(),
                        toOffsetDateTime(startedAt),
                        toOffsetDateTime(completedAt),
                        actualDurationMinutes,
                        myTeamLessonPrice
                ),
                instructorProfile(lesson.getInstructorProfile())
        );
    }

    private ConsumerLessonDetailResponse canceledResponse(
            Lesson lesson,
            List<LessonParticipant> participants,
            int myTeamLessonPrice,
            Optional<LessonCancellation> myCancellation,
            Optional<LessonCancellation> instructorCancellation
    ) {
        // 내 팀 취소와 강사 취소가 모두 있을 수 있으므로 가장 최근 취소 정보를 보여줌
        // TODO: 관리자 취소 기능 추가시 수정 필여
        // TODO: 취소 및 환불 정책 결정 후 수정 필요
        LessonCancellation cancellation = Stream.of(myCancellation, instructorCancellation)
                .flatMap(Optional::stream)
                .max(Comparator.comparing(LessonCancellation::getCanceledAt))
                .orElseThrow(() -> new BusinessException(LessonErrorCode.LESSON_CANCELLATION_NOT_FOUND));
        ConsumerLessonDetailResponse.CanceledByResponse canceledBy;
        if (cancellation.getCanceledBy() == LessonCancellationActor.INSTRUCTOR) {
            InstructorProfile instructorProfile = lesson.getInstructorProfile();
            canceledBy = ConsumerLessonDetailResponse.CanceledByResponse.of(
                    instructorProfile.getMember().getId(),
                    instructorProfile.getRealName()
                );
        } else {
            Member member = cancellation.getMember();
            canceledBy = ConsumerLessonDetailResponse.CanceledByResponse.of(member.getId(), member.getNickname());
        }

        return ConsumerLessonDetailResponse.canceled(
                lesson.getId(),
                ConsumerLessonDetailResponse.CancelInfoResponse.of(
                        toOffsetDateTime(cancellation.getCanceledAt()),
                        canceledBy,
                        cancellation.getCancelReason()
                ),
                ConsumerLessonDetailResponse.CanceledLessonInfoResponse.of(
                        representativeConsumerNames(participants),
                        lesson.getTotalHeadcount(),
                        resort(lesson.getResort()),
                        lesson.getSport(),
                        lesson.getLessonLevel(),
                        lesson.getDurationMinutes(),
                        myTeamLessonPrice
                ),
                instructorProfile(lesson.getInstructorProfile())
        );
    }

    private ConsumerLessonDetailResponse.LessonInfoResponse activeLessonInfo(
            Lesson lesson,
            List<LessonParticipant> participants,
            int myTeamLessonPrice
    ) {
        return ConsumerLessonDetailResponse.LessonInfoResponse.of(
                representativeConsumerNames(participants),
                lesson.getTotalHeadcount(),
                resort(lesson.getResort()),
                lesson.getSport(),
                lesson.getLessonLevel(),
                toOffsetDateTime(lesson.getScheduledAt()),
                lesson.getDurationMinutes(),
                myTeamLessonPrice
        );
    }

    private ConsumerLessonDetailResponse.InstructorProfileResponse instructorProfile(InstructorProfile profile) {
        Member member = profile.getMember();
        return ConsumerLessonDetailResponse.InstructorProfileResponse.of(
                profile.getId(),
                profile.getRealName(),
                profile.getGender(),
                profile.getBirthDate().getYear(),
                profile.getLevel(),
                member.getProfileImageUrl()
        );
    }

    private List<ConsumerLessonDetailResponse.ConfirmedMatchingRequestResponse> confirmedMatchingRequests(
            List<LessonParticipant> participants,
            Map<Long, LessonStartConfirmation> confirmedByMatchingRequestId
    ) {
        return groupedParticipants(participants)
                .values()
                .stream()
                .map(group -> confirmedMatchingRequest(group, confirmedByMatchingRequestId))
                .toList();
    }

    private ConsumerLessonDetailResponse.ConfirmedMatchingRequestResponse confirmedMatchingRequest(
            List<LessonParticipant> participants,
            Map<Long, LessonStartConfirmation> confirmedByMatchingRequestId
    ) {
        LessonParticipant firstParticipant = firstParticipant(participants);
        MatchingRequest matchingRequest = firstParticipant.getMatchingRequest();
        Member representativeMember = matchingRequest.getMember();
        LessonStartConfirmation confirmation = confirmedByMatchingRequestId.get(matchingRequest.getId());
        List<ConsumerLessonDetailResponse.ParticipantResponse> participantResponses = participants.stream()
                .map(participant -> ConsumerLessonDetailResponse.ParticipantResponse.of(
                        participant.getId(),
                        participant.getGender(),
                        participant.getAge()
                ))
                .toList();

        return ConsumerLessonDetailResponse.ConfirmedMatchingRequestResponse.of(
                matchingRequest.getId(),
                representativeMember.getId(),
                representativeMember.getNickname(),
                matchingRequest.getHeadcount(),
                confirmation != null,
                participantResponses
        );
    }

    private List<ConsumerLessonDetailResponse.MatchingRequestResponse> matchingRequests(
            List<LessonParticipant> participants
    ) {
        return groupedParticipants(participants)
                .values()
                .stream()
                .map(this::matchingRequest)
                .toList();
    }

    private ConsumerLessonDetailResponse.MatchingRequestResponse matchingRequest(
            List<LessonParticipant> participants
    ) {
        LessonParticipant firstParticipant = firstParticipant(participants);
        MatchingRequest matchingRequest = firstParticipant.getMatchingRequest();
        Member representativeMember = matchingRequest.getMember();
        List<ConsumerLessonDetailResponse.ParticipantResponse> participantResponses = participants.stream()
                .map(participant -> ConsumerLessonDetailResponse.ParticipantResponse.of(
                        participant.getId(),
                        participant.getGender(),
                        participant.getAge()
                ))
                .toList();

        return ConsumerLessonDetailResponse.MatchingRequestResponse.of(
                matchingRequest.getId(),
                representativeMember.getId(),
                representativeMember.getNickname(),
                matchingRequest.getHeadcount(),
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

    private boolean isConfirmedInstructor(LessonStartConfirmation confirmation) {
        return confirmation.getActorType() == LessonStartConfirmationActor.INSTRUCTOR
                && confirmation.getStatus() == LessonStartConfirmationStatus.CONFIRMED;
    }

    private boolean isConfirmedConsumer(LessonStartConfirmation confirmation) {
        return confirmation.getActorType() == LessonStartConfirmationActor.CONSUMER
                && confirmation.getStatus() == LessonStartConfirmationStatus.CONFIRMED;
    }

    private LessonParticipant firstParticipant(List<LessonParticipant> participants) {
        return participants.stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(LessonErrorCode.LESSON_INVALID_STATE));
    }

    private ConsumerLessonDetailResponse.ResortResponse resort(Resort resort) {
        return ConsumerLessonDetailResponse.ResortResponse.of(resort.getCode(), resort.getDisplayName());
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
