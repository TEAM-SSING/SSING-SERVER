package org.sopt.ssingserver.domain.matching.dto.command;

import org.sopt.ssingserver.domain.member.enums.Gender;

// 소비자 매칭 요청 참여자 1명의 이름/나이/성별 입력값 Service 전달
public record MatchingParticipantCommand(
        String name,
        int age,
        Gender gender
) {

    public MatchingParticipantCommand {
        name = name == null ? null : name.strip();
    }

    public MatchingParticipantCommand(int age, Gender gender) {
        this(null, age, gender);
    }

    // 요청 DTO의 참여자 command 생성용 작은 팩토리 메서드
    public static MatchingParticipantCommand of(
            String name,
            int age,
            Gender gender
    ) {
        return new MatchingParticipantCommand(name, age, gender);
    }

    public static MatchingParticipantCommand of(
            int age,
            Gender gender
    ) {
        return of(null, age, gender);
    }
}
