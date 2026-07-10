package org.sopt.ssingserver.domain.lesson.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;
import org.sopt.ssingserver.domain.member.enums.Gender;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InstructorLessonDetailResponse(
        @Schema(description = "강습 ID", example = "9101")
        Long lessonId,

        @Schema(description = "강습 상태", example = "CONFIRMED")
        LessonStatus lessonStatus,

        @Schema(description = "상태별 강습 진행 정보")
        Object statusInfo,

        @Schema(description = "취소 상태 정보. lessonStatus가 CANCELED일 때 사용")
        CancelInfoResponse cancelInfo,

        @Schema(description = "상태별 강습 정보")
        Object lessonInfo,

        @Schema(description = "강습에 포함된 팀 목록")
        List<?> matchingRequests
) {

    public static InstructorLessonDetailResponse confirmed(
            Long lessonId,
            ConfirmedStatusInfoResponse statusInfo,
            LessonInfoResponse lessonInfo,
            List<ConfirmedMatchingRequestResponse> matchingRequests
    ) {
        return new InstructorLessonDetailResponse(
                lessonId,
                LessonStatus.CONFIRMED,
                statusInfo,
                null,
                lessonInfo,
                matchingRequests
        );
    }

    public static InstructorLessonDetailResponse inProgress(
            Long lessonId,
            InProgressStatusInfoResponse statusInfo,
            LessonInfoResponse lessonInfo,
            List<MatchingRequestResponse> matchingRequests
    ) {
        return new InstructorLessonDetailResponse(
                lessonId,
                LessonStatus.IN_PROGRESS,
                statusInfo,
                null,
                lessonInfo,
                matchingRequests
        );
    }

    public static InstructorLessonDetailResponse completed(
            Long lessonId,
            CompletedLessonInfoResponse lessonInfo,
            List<MatchingRequestResponse> matchingRequests
    ) {
        return new InstructorLessonDetailResponse(
                lessonId,
                LessonStatus.COMPLETED,
                null,
                null,
                lessonInfo,
                matchingRequests
        );
    }

    public static InstructorLessonDetailResponse canceled(
            Long lessonId,
            CancelInfoResponse cancelInfo,
            CanceledLessonInfoResponse lessonInfo,
            List<MatchingRequestResponse> matchingRequests
    ) {
        return new InstructorLessonDetailResponse(
                lessonId,
                LessonStatus.CANCELED,
                null,
                cancelInfo,
                lessonInfo,
                matchingRequests
        );
    }

    public record ConfirmedStatusInfoResponse(
            @Schema(description = "강습 시작을 누른 강사와 팀 수", example = "2")
            int confirmedCount,

            @Schema(description = "강습 시작을 눌러야 하는 강사와 팀 수", example = "3")
            int requiredCount,

            @Schema(description = "강사의 강습 시작 준비 완료 여부", example = "true")
            boolean instructorConfirmed
    ) {

        public static ConfirmedStatusInfoResponse of(
                int confirmedCount,
                int requiredCount,
                boolean instructorConfirmed
        ) {
            return new ConfirmedStatusInfoResponse(confirmedCount, requiredCount, instructorConfirmed);
        }
    }

    public record InProgressStatusInfoResponse(
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
    ) {

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

    public record CancelInfoResponse(
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

    public record CanceledByResponse(
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

    public record LessonInfoResponse(
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

            @Schema(description = "전체 강습 가격", example = "80000")
            int totalLessonPrice
    ) {

        public static LessonInfoResponse of(
                List<String> representativeConsumerNames,
                int totalHeadcount,
                ResortResponse resort,
                Sport sport,
                LessonLevel lessonLevel,
                OffsetDateTime scheduledAt,
                int scheduledDurationMinutes,
                int totalLessonPrice
        ) {
            return new LessonInfoResponse(
                    representativeConsumerNames,
                    totalHeadcount,
                    resort,
                    sport,
                    lessonLevel,
                    scheduledAt,
                    scheduledDurationMinutes,
                    totalLessonPrice
            );
        }
    }

    public record CompletedLessonInfoResponse(
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

            @Schema(description = "전체 강습 가격", example = "80000")
            int totalLessonPrice
    ) {

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
                int totalLessonPrice
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
                    totalLessonPrice
            );
        }
    }

    public record CanceledLessonInfoResponse(
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

            @Schema(description = "전체 강습 가격", example = "80000")
            int totalLessonPrice
    ) {

        public static CanceledLessonInfoResponse of(
                List<String> representativeConsumerNames,
                int totalHeadcount,
                ResortResponse resort,
                Sport sport,
                LessonLevel lessonLevel,
                int lessonDurationMinutes,
                int totalLessonPrice
        ) {
            return new CanceledLessonInfoResponse(
                    representativeConsumerNames,
                    totalHeadcount,
                    resort,
                    sport,
                    lessonLevel,
                    lessonDurationMinutes,
                    totalLessonPrice
            );
        }
    }

    public record ResortResponse(
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

    public record ConfirmedMatchingRequestResponse(
            @Schema(description = "매칭 요청 ID", example = "91011")
            Long matchingRequestId,

            @Schema(description = "대표 회원 ID", example = "9001")
            Long representativeMemberId,

            @Schema(description = "대표 회원 이름", example = "김OO")
            String representativeMemberName,

            @Schema(description = "해당 팀 인원 수", example = "3")
            int headcount,

            @Schema(description = "해당 팀 강습 가격", example = "40000")
            int teamLessonPrice,

            @Schema(description = "해당 팀의 강습 시작 준비 완료 여부", example = "true")
            boolean startConfirmed,

            @Schema(description = "해당 팀 참여자 목록")
            List<ParticipantResponse> participants
    ) {

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

    public record MatchingRequestResponse(
            @Schema(description = "매칭 요청 ID", example = "91011")
            Long matchingRequestId,

            @Schema(description = "대표 회원 ID", example = "9001")
            Long representativeMemberId,

            @Schema(description = "대표 회원 이름", example = "김OO")
            String representativeMemberName,

            @Schema(description = "해당 팀 인원 수", example = "3")
            int headcount,

            @Schema(description = "해당 팀 강습 가격", example = "40000")
            int teamLessonPrice,

            @Schema(description = "해당 팀 참여자 목록")
            List<ParticipantResponse> participants
    ) {

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

    public record ParticipantResponse(
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
