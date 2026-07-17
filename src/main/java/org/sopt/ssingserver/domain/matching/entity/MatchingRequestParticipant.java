package org.sopt.ssingserver.domain.matching.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sopt.ssingserver.domain.member.enums.Gender;
import org.sopt.ssingserver.global.entity.BaseTimeEntity;

@Getter
@Entity
@Table(name = "matching_request_participants")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchingRequestParticipant extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private MatchingRequest matchingRequest;

    @Column(length = 50)
    private String name;

    @Column(nullable = false)
    private int age;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Gender gender;

    public static MatchingRequestParticipant create(
            MatchingRequest matchingRequest,
            String name,
            int age,
            Gender gender
    ) {
        MatchingRequestParticipant participant = new MatchingRequestParticipant();
        participant.matchingRequest = matchingRequest;
        participant.name = name == null ? null : name.strip();
        participant.age = age;
        participant.gender = gender;
        return participant;
    }

    public static MatchingRequestParticipant create(
            MatchingRequest matchingRequest,
            int age,
            Gender gender
    ) {
        return create(matchingRequest, null, age, gender);
    }
}
