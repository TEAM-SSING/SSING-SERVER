package org.sopt.ssingserver.domain.matching.dto.command;

import java.util.List;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.resort.entity.Resort;

// Controller/API 요청값의 Service 내부 매칭 생성 command 집계
public record MatchingCreationCommand(
        Member member,
        Resort resort,
        Sport sport,
        LessonLevel lessonLevel,
        int headcount,
        int requestedDurationMinutes,
        boolean isEquipmentReady,
        List<MatchingParticipantCommand> participants
) {

    // 외부 참여자 목록 수정으로 인한 저장 기준 변경 방지용 불변 복사
    public MatchingCreationCommand {
        participants = List.copyOf(participants);
    }

    // Controller의 request DTO -> command 변환용 정적 생성자
    public static MatchingCreationCommand of(
            Member member,
            Resort resort,
            Sport sport,
            LessonLevel lessonLevel,
            int headcount,
            int requestedDurationMinutes,
            boolean isEquipmentReady,
            List<MatchingParticipantCommand> participants
    ) {
        return new MatchingCreationCommand(
                member,
                resort,
                sport,
                lessonLevel,
                headcount,
                requestedDurationMinutes,
                isEquipmentReady,
                participants
        );
    }
}
