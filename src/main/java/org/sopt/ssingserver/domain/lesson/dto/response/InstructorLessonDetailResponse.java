package org.sopt.ssingserver.domain.lesson.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;
import org.sopt.ssingserver.domain.member.enums.Gender;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
        name = "InstructorLessonDetailResponse",
        description = "강사 강습 상세 응답. lessonStatus에 따라 응답 구조가 달라집니다.",
        discriminatorProperty = "lessonStatus",
        discriminatorMapping = {
                @DiscriminatorMapping(value = "CONFIRMED", schema = InstructorLessonDetailResponse.Confirmed.class),
                @DiscriminatorMapping(value = "IN_PROGRESS", schema = InstructorLessonDetailResponse.InProgress.class),
                @DiscriminatorMapping(value = "COMPLETED", schema = InstructorLessonDetailResponse.Completed.class),
                @DiscriminatorMapping(value = "CANCELED", schema = InstructorLessonDetailResponse.Canceled.class)
        },
        oneOf = {
                InstructorLessonDetailResponse.Confirmed.class,
                InstructorLessonDetailResponse.InProgress.class,
                InstructorLessonDetailResponse.Completed.class,
                InstructorLessonDetailResponse.Canceled.class
        }
)
public sealed interface InstructorLessonDetailResponse permits
        InstructorLessonDetailResponse.Confirmed,
        InstructorLessonDetailResponse.InProgress,
        InstructorLessonDetailResponse.Completed,
        InstructorLessonDetailResponse.Canceled {

    @Schema(description = "강습 ID", example = "9101")
    Long lessonId();

    @Schema(description = "강습 상태", example = "CONFIRMED")
    LessonStatus lessonStatus();

    default StatusInfo statusInfo() {
        return null;
    }

    default CancelInfoResponse cancelInfo() {
        return null;
    }

    default LessonInfo lessonInfo() {
        return null;
    }

    default List<? extends MatchingRequest> matchingRequests() {
        return null;
    }

    static InstructorLessonDetailResponse confirmed(
            Long lessonId,
            ConfirmedStatusInfoResponse statusInfo,
            LessonInfoResponse lessonInfo,
            List<ConfirmedMatchingRequestResponse> matchingRequests
    ) {
        return new Confirmed(
                lessonId,
                LessonStatus.CONFIRMED,
                statusInfo,
                lessonInfo,
                matchingRequests
        );
    }

    static InstructorLessonDetailResponse inProgress(
            Long lessonId,
            InProgressStatusInfoResponse statusInfo,
            LessonInfoResponse lessonInfo,
            List<MatchingRequestResponse> matchingRequests
    ) {
        return new InProgress(
                lessonId,
                LessonStatus.IN_PROGRESS,
                statusInfo,
                lessonInfo,
                matchingRequests
        );
    }

    static InstructorLessonDetailResponse completed(
            Long lessonId,
            CompletedLessonInfoResponse lessonInfo,
            List<MatchingRequestResponse> matchingRequests
    ) {
        return new Completed(
                lessonId,
                LessonStatus.COMPLETED,
                lessonInfo,
                matchingRequests
        );
    }

    static InstructorLessonDetailResponse canceled(
            Long lessonId,
            CancelInfoResponse cancelInfo,
            CanceledLessonInfoResponse lessonInfo,
            List<MatchingRequestResponse> matchingRequests
    ) {
        return new Canceled(
                lessonId,
                LessonStatus.CANCELED,
                cancelInfo,
                lessonInfo,
                matchingRequests
        );
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "InstructorLessonConfirmedDetail", description = "강사 강습 상세 - 시작 전")
    record Confirmed(
            @Schema(description = "강습 ID", example = "9101")
            Long lessonId,

            @Schema(description = "강습 상태", example = "CONFIRMED")
            LessonStatus lessonStatus,

            @Schema(description = "강습 시작 확인 현황")
            ConfirmedStatusInfoResponse statusInfo,

            @Schema(description = "강습 정보")
            LessonInfoResponse lessonInfo,

            @Schema(description = "강습에 포함된 팀 목록")
            List<ConfirmedMatchingRequestResponse> matchingRequests
    ) implements InstructorLessonDetailResponse {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "InstructorLessonInProgressDetail", description = "강사 강습 상세 - 진행 중")
    record InProgress(
            @Schema(description = "강습 ID", example = "9101")
            Long lessonId,

            @Schema(description = "강습 상태", example = "IN_PROGRESS")
            LessonStatus lessonStatus,

            @Schema(description = "진행 중 강습 시간 정보")
            InProgressStatusInfoResponse statusInfo,

            @Schema(description = "강습 정보")
            LessonInfoResponse lessonInfo,

            @Schema(description = "강습에 포함된 팀 목록")
            List<MatchingRequestResponse> matchingRequests
    ) implements InstructorLessonDetailResponse {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "InstructorLessonCompletedDetail", description = "강사 강습 상세 - 완료")
    record Completed(
            @Schema(description = "강습 ID", example = "9101")
            Long lessonId,

            @Schema(description = "강습 상태", example = "COMPLETED")
            LessonStatus lessonStatus,

            @Schema(description = "완료된 강습 정보")
            CompletedLessonInfoResponse lessonInfo,

            @Schema(description = "강습에 포함된 팀 목록")
            List<MatchingRequestResponse> matchingRequests
    ) implements InstructorLessonDetailResponse {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "InstructorLessonCanceledDetail", description = "강사 강습 상세 - 취소")
    record Canceled(
            @Schema(description = "강습 ID", example = "9101")
            Long lessonId,

            @Schema(description = "강습 상태", example = "CANCELED")
            LessonStatus lessonStatus,

            @Schema(description = "취소 상태 정보")
            CancelInfoResponse cancelInfo,

            @Schema(description = "취소된 강습 정보")
            CanceledLessonInfoResponse lessonInfo,

            @Schema(description = "강습에 포함된 팀 목록")
            List<MatchingRequestResponse> matchingRequests
    ) implements InstructorLessonDetailResponse {
    }

    sealed interface StatusInfo permits ConfirmedStatusInfoResponse, InProgressStatusInfoResponse {
    }

    sealed interface LessonInfo permits LessonInfoResponse, CompletedLessonInfoResponse, CanceledLessonInfoResponse {
    }

    sealed interface MatchingRequest permits ConfirmedMatchingRequestResponse, MatchingRequestResponse {
    }

    record ConfirmedStatusInfoResponse(
            @Schema(description = "강습 준비가 완료된 사람 수", example = "4")
            int confirmedCount,

            @Schema(description = "강습 준비를 눌러야 하는 전체 사람 수", example = "6")
            int requiredCount,

            @Schema(description = "현재 요청 주체의 강습 시작 준비 완료 여부", example = "true")
            boolean currentActorConfirmed,

            @Schema(description = "강사의 강습 시작 준비 완료 여부", example = "true")
            boolean instructorConfirmed
    ) implements StatusInfo {

        public static ConfirmedStatusInfoResponse of(
                int confirmedCount,
                int requiredCount,
                boolean currentActorConfirmed,
                boolean instructorConfirmed
        ) {
            return new ConfirmedStatusInfoResponse(
                    confirmedCount,
                    requiredCount,
                    currentActorConfirmed,
                    instructorConfirmed
            );
        }
    }

    record InProgressStatusInfoResponse(
            @Schema(description = "서버 현재 시각", example = "2026-01-01T10:59:00+09:00")
            OffsetDateTime serverTime,

            @Schema(description = "실제 강습 시작 시각", example = "2026-01-01T10:00:00+09:00")
            OffsetDateTime actualStartedAt,

            @Schema(description = "예상 강습 종료 시각", example = "2026-01-01T12:00:00+09:00")
            OffsetDateTime expectedEndedAt,

            @Schema(description = "강습 시작 후 경과 시간", example = "3540")
            long elapsedSeconds,

            @Schema(description = "강습 종료까지 남은 시간", example = "3660")
            long remainingSeconds
    ) implements StatusInfo {

        public static InProgressStatusInfoResponse of(
                OffsetDateTime serverTime,
                OffsetDateTime actualStartedAt,
                OffsetDateTime expectedEndedAt,
                long elapsedSeconds,
                long remainingSeconds
        ) {
            return new InProgressStatusInfoResponse(
                    serverTime,
                    actualStartedAt,
                    expectedEndedAt,
                    elapsedSeconds,
                    remainingSeconds
            );
        }
    }

    record CancelInfoResponse(
            @Schema(description = "취소 시각", example = "2026-01-01T10:20:00+09:00")
            OffsetDateTime canceledAt,

            @Schema(description = "취소 주체 정보")
            CanceledByResponse canceledBy,

            @Schema(description = "취소 사유", example = "일정 변경")
            String cancelReason
    ) {

        public static CancelInfoResponse of(
                OffsetDateTime canceledAt,
                CanceledByResponse canceledBy,
                String cancelReason
        ) {
            return new CancelInfoResponse(canceledAt, canceledBy, cancelReason);
        }
    }

    record CanceledByResponse(
            @Schema(description = "취소한 회원 ID", example = "9001")
            Long memberId,

            @Schema(description = "취소한 회원 이름", example = "김OO")
            String name
    ) {

        public static CanceledByResponse of(
                Long memberId,
                String name
        ) {
            return new CanceledByResponse(memberId, name);
        }
    }

    record LessonInfoResponse(
            @Schema(description = "팀 대표 소비자 이름 목록", example = "[\"김OO\", \"홍지민\"]")
            List<String> representativeConsumerNames,

            @Schema(description = "전체 강습 인원 수", example = "5")
            int totalHeadcount,

            @Schema(description = "리조트 정보")
            ResortResponse resort,

            @Schema(description = "종목", example = "SNOWBOARD")
            Sport sport,

            @Schema(description = "강습 레벨", example = "FIRST_TIME")
            LessonLevel lessonLevel,

            @Schema(description = "강습 예정 시각", example = "2026-01-01T10:00:00+09:00")
            OffsetDateTime scheduledAt,

            @Schema(description = "예정 강습 시간", example = "120")
            int scheduledDurationMinutes,

            @Schema(description = "호환용 강사 정산 금액. instructorSettlementAmount와 동일", example = "80000", deprecated = true)
            int totalLessonPrice,

            @Schema(description = "패찰비를 제외한 강사 자기 정산 금액", example = "80000")
            int instructorSettlementAmount
    ) implements LessonInfo {

        public static LessonInfoResponse of(
                List<String> representativeConsumerNames,
                int totalHeadcount,
                ResortResponse resort,
                Sport sport,
                LessonLevel lessonLevel,
                OffsetDateTime scheduledAt,
                int scheduledDurationMinutes,
                int instructorSettlementAmount
        ) {
            return new LessonInfoResponse(
                    representativeConsumerNames,
                    totalHeadcount,
                    resort,
                    sport,
                    lessonLevel,
                    scheduledAt,
                    scheduledDurationMinutes,
                    instructorSettlementAmount,
                    instructorSettlementAmount
            );
        }
    }

    record CompletedLessonInfoResponse(
            @Schema(description = "팀 대표 소비자 이름 목록", example = "[\"김OO\", \"홍지민\"]")
            List<String> representativeConsumerNames,

            @Schema(description = "전체 강습 인원 수", example = "5")
            int totalHeadcount,

            @Schema(description = "리조트 정보")
            ResortResponse resort,

            @Schema(description = "종목", example = "SNOWBOARD")
            Sport sport,

            @Schema(description = "강습 레벨", example = "FIRST_TIME")
            LessonLevel lessonLevel,

            @Schema(description = "강습 기본 시간", example = "120")
            int lessonDurationMinutes,

            @Schema(description = "실제 강습 시작 시각", example = "2026-01-01T10:00:00+09:00")
            OffsetDateTime actualStartedAt,

            @Schema(description = "실제 강습 종료 시각", example = "2026-01-01T11:58:00+09:00")
            OffsetDateTime actualEndedAt,

            @Schema(description = "실제 강습 진행 시간", example = "118")
            int actualDurationMinutes,

            @Schema(description = "호환용 강사 정산 금액. instructorSettlementAmount와 동일", example = "80000", deprecated = true)
            int totalLessonPrice,

            @Schema(description = "패찰비를 제외한 강사 자기 정산 금액", example = "80000")
            int instructorSettlementAmount
    ) implements LessonInfo {

        public static CompletedLessonInfoResponse of(
                List<String> representativeConsumerNames,
                int totalHeadcount,
                ResortResponse resort,
                Sport sport,
                LessonLevel lessonLevel,
                int lessonDurationMinutes,
                OffsetDateTime actualStartedAt,
                OffsetDateTime actualEndedAt,
                int actualDurationMinutes,
                int instructorSettlementAmount
        ) {
            return new CompletedLessonInfoResponse(
                    representativeConsumerNames,
                    totalHeadcount,
                    resort,
                    sport,
                    lessonLevel,
                    lessonDurationMinutes,
                    actualStartedAt,
                    actualEndedAt,
                    actualDurationMinutes,
                    instructorSettlementAmount,
                    instructorSettlementAmount
            );
        }
    }

    record CanceledLessonInfoResponse(
            @Schema(description = "팀 대표 소비자 이름 목록", example = "[\"김OO\", \"홍지민\"]")
            List<String> representativeConsumerNames,

            @Schema(description = "전체 강습 인원 수", example = "5")
            int totalHeadcount,

            @Schema(description = "리조트 정보")
            ResortResponse resort,

            @Schema(description = "종목", example = "SNOWBOARD")
            Sport sport,

            @Schema(description = "강습 레벨", example = "FIRST_TIME")
            LessonLevel lessonLevel,

            @Schema(description = "강습 기본 시간", example = "120")
            int lessonDurationMinutes,

            @Schema(description = "호환용 강사 정산 금액. instructorSettlementAmount와 동일", example = "80000", deprecated = true)
            int totalLessonPrice,

            @Schema(description = "패찰비를 제외한 강사 자기 정산 금액", example = "80000")
            int instructorSettlementAmount
    ) implements LessonInfo {

        public static CanceledLessonInfoResponse of(
                List<String> representativeConsumerNames,
                int totalHeadcount,
                ResortResponse resort,
                Sport sport,
                LessonLevel lessonLevel,
                int lessonDurationMinutes,
                int instructorSettlementAmount
        ) {
            return new CanceledLessonInfoResponse(
                    representativeConsumerNames,
                    totalHeadcount,
                    resort,
                    sport,
                    lessonLevel,
                    lessonDurationMinutes,
                    instructorSettlementAmount,
                    instructorSettlementAmount
            );
        }
    }

    record ResortResponse(
            @Schema(description = "리조트 코드", example = "HIGH1")
            String code,

            @Schema(description = "Android 표시용 리조트 이름", example = "하이원")
            String displayName
    ) {

        public static ResortResponse of(
                String code,
                String displayName
        ) {
            return new ResortResponse(code, displayName);
        }
    }

    record ConfirmedMatchingRequestResponse(
            @Schema(description = "매칭 요청 ID", example = "91011")
            Long matchingRequestId,

            @Schema(description = "대표 회원 ID", example = "9001")
            Long representativeMemberId,

            @Schema(description = "대표 회원 이름", example = "김OO")
            String representativeMemberName,

            @Schema(description = "해당 팀 인원 수", example = "3")
            int headcount,

            @Schema(description = "해당 팀의 패찰비 제외 강습비 snapshot", example = "40000")
            int teamLessonPrice,

            @Schema(description = "해당 팀의 강습 시작 준비 완료 여부", example = "true")
            boolean startConfirmed,

            @Schema(description = "해당 팀 참여자 목록")
            List<ParticipantResponse> participants
    ) implements MatchingRequest {

        public static ConfirmedMatchingRequestResponse of(
                Long matchingRequestId,
                Long representativeMemberId,
                String representativeMemberName,
                int headcount,
                int teamLessonPrice,
                boolean startConfirmed,
                List<ParticipantResponse> participants
        ) {
            return new ConfirmedMatchingRequestResponse(
                    matchingRequestId,
                    representativeMemberId,
                    representativeMemberName,
                    headcount,
                    teamLessonPrice,
                    startConfirmed,
                    participants
            );
        }
    }

    record MatchingRequestResponse(
            @Schema(description = "매칭 요청 ID", example = "91011")
            Long matchingRequestId,

            @Schema(description = "대표 회원 ID", example = "9001")
            Long representativeMemberId,

            @Schema(description = "대표 회원 이름", example = "김OO")
            String representativeMemberName,

            @Schema(description = "해당 팀 인원 수", example = "3")
            int headcount,

            @Schema(description = "해당 팀의 패찰비 제외 강습비 snapshot", example = "40000")
            int teamLessonPrice,

            @Schema(description = "해당 팀 참여자 목록")
            List<ParticipantResponse> participants
    ) implements MatchingRequest {

        public static MatchingRequestResponse of(
                Long matchingRequestId,
                Long representativeMemberId,
                String representativeMemberName,
                int headcount,
                int teamLessonPrice,
                List<ParticipantResponse> participants
        ) {
            return new MatchingRequestResponse(
                    matchingRequestId,
                    representativeMemberId,
                    representativeMemberName,
                    headcount,
                    teamLessonPrice,
                    participants
            );
        }
    }

    record ParticipantResponse(
            @Schema(description = "강습 참여자 ID", example = "910111")
            Long participantId,

            @Schema(description = "강습 참여자 성별", example = "MALE")
            Gender gender,

            @Schema(description = "강습 참여자 나이", example = "38")
            int age
    ) {

        public static ParticipantResponse of(
                Long participantId,
                Gender gender,
                int age
        ) {
            return new ParticipantResponse(participantId, gender, age);
        }
    }
}
