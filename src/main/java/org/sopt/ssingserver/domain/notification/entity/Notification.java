package org.sopt.ssingserver.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.notification.enums.ClientApp;
import org.sopt.ssingserver.domain.notification.enums.NotificationType;
import org.sopt.ssingserver.global.entity.BaseTimeEntity;

@Getter
@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(
                        name = "idx_notifications_member_created_id",
                        columnList = "member_id, created_at, id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

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
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    @Column(columnDefinition = "json")
    private String dataJson;

    private Instant readAt;

    public static Notification create(
            Member member,
            ClientApp clientApp,
            NotificationType type,
            String title,
            String body,
            String dataJson
    ) {
        Notification notification = new Notification();
        notification.member = member;
        notification.clientApp = clientApp;
        notification.type = type;
        notification.title = title;
        notification.body = body;
        notification.dataJson = dataJson;
        return notification;
    }

    public boolean isRead() {
        return readAt != null;
    }

    public void markRead(Instant readAt) {
        if (isRead()) {
            return;
        }
        this.readAt = readAt;
    }
}
