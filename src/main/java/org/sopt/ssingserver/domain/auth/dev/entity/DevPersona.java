package org.sopt.ssingserver.domain.auth.dev.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sopt.ssingserver.domain.auth.dev.enums.DevPersonaTemplate;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.global.entity.BaseTimeEntity;

@Getter
@Entity
@Table(
        name = "dev_personas",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_dev_personas_persona_key", columnNames = "persona_key"),
                @UniqueConstraint(name = "uk_dev_personas_member", columnNames = "member_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DevPersona extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String personaKey;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private DevPersonaTemplate template;

    public static DevPersona create(
            String personaKey,
            Member member,
            DevPersonaTemplate template
    ) {
        DevPersona devPersona = new DevPersona();
        devPersona.personaKey = personaKey;
        devPersona.member = member;
        devPersona.template = template;
        return devPersona;
    }
}
