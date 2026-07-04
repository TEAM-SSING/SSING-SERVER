package org.sopt.ssingserver.domain.notification.entity;

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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.notification.enums.ClientApp;
import org.sopt.ssingserver.domain.notification.enums.ClientPlatform;
import org.sopt.ssingserver.global.entity.BaseTimeEntity;

@Getter
@Entity
@Table(
        name = "fcm_tokens",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_fcm_tokens_token",
                columnNames = "token"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FcmToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClientApp clientApp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ClientPlatform platform;

    @Column(nullable = false, length = 512)
    private String token;

    @Column(nullable = false)
    private Instant lastRegisteredAt;

    public static FcmToken create(
            Member member,
            ClientApp clientApp,
            ClientPlatform platform,
            String token,
            Instant registeredAt
    ) {
        FcmToken fcmToken = new FcmToken();
        fcmToken.member = member;
        fcmToken.clientApp = clientApp;
        fcmToken.platform = platform;
        fcmToken.token = token;
        fcmToken.lastRegisteredAt = registeredAt;
        return fcmToken;
    }

    public void updateRegistration(
            Member member,
            ClientApp clientApp,
            ClientPlatform platform,
            Instant registeredAt
    ) {
        this.member = member;
        this.clientApp = clientApp;
        this.platform = platform;
        this.lastRegisteredAt = registeredAt;
    }
}
