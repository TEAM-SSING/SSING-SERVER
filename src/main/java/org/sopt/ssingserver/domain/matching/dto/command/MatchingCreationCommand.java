package org.sopt.ssingserver.domain.matching.dto.command;

import java.util.List;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;

// Controller/API 요청값의 Service 내부 매칭 생성 command 집계
public record MatchingCreationCommand(
        // 인증/접근 계층에서 확정된 소비자 회원 id, Service의 회원 엔티티 조회 기준
        Long memberId,
        // 요청 DTO의 리조트 id, Service의 리조트 존재 검증과 요청 소속 저장 기준
        Long resortId,
        // 강사 노출 조건과 비교할 종목 조건
        Sport sport,
        // 강사 노출 조건과 비교할 레슨 레벨 조건
        LessonLevel lessonLevel,
        // 매칭 요청 총 인원, 참여자 목록 수 검증과 강사 수용 가능 인원 비교 기준
        int headcount,
        // 강사 노출 시간 조건과 교집합으로 비교할 희망 수업 시간 목록
        List<Integer> requestedDurationMinutes,
        // 장비 준비 여부 조건, 강사 노출 조건의 장비 포함 가능 여부 비교 기준
        boolean isEquipmentReady,
        // 요청 참여자별 나이/성별 저장값, headcount와 같은 인원 기준 유지 대상
        List<MatchingParticipantCommand> participants
) {

    // 외부 목록 수정으로 인한 희망 시간/참여자 저장 기준 변경 방지용 불변 복사
    public MatchingCreationCommand {
        requestedDurationMinutes = List.copyOf(requestedDurationMinutes);
        participants = List.copyOf(participants);
    }

    // Controller의 request DTO -> command 변환용 정적 생성자
    public static MatchingCreationCommand of(
            Long memberId,
            Long resortId,
            Sport sport,
            LessonLevel lessonLevel,
            int headcount,
            List<Integer> requestedDurationMinutes,
            boolean isEquipmentReady,
            List<MatchingParticipantCommand> participants
    ) {
        return new MatchingCreationCommand(
                memberId,
                resortId,
                sport,
                lessonLevel,
                headcount,
                requestedDurationMinutes,
                isEquipmentReady,
                participants
        );
    }
}
