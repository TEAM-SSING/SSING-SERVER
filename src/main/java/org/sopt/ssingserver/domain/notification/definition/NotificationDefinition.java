package org.sopt.ssingserver.domain.notification.definition;

import org.sopt.ssingserver.domain.notification.enums.ClientApp;
import org.sopt.ssingserver.domain.notification.enums.NotificationType;

public enum NotificationDefinition {

    MATCHING_OFFER_RECEIVED(
            NotificationType.MATCHING_OFFER_RECEIVED,
            ClientApp.INSTRUCTOR,
            "씽 매칭 강습 도착",
            "새로운 강습이 도착했어요. 강습생 정보를 확인하고 강습을 수락해보세요.",
            "https://ssing.app/instructor-matching",
            Target.NONE,
            Target.OFFER_ID,
            Target.OFFER_ID
    ),
    MATCHING_OFFER_CLOSED(
            NotificationType.MATCHING_OFFER_CLOSED,
            ClientApp.INSTRUCTOR,
            "강습 거절",
            "요청 받았던 강습이 거절되었어요. 다른 요청을 받아볼까요?",
            "https://ssing.app/instructor-matching",
            Target.NONE,
            Target.OFFER_ID,
            Target.OFFER_ID
    ),
    MATCHING_CONFIRMED(
            NotificationType.MATCHING_CONFIRMED,
            ClientApp.INSTRUCTOR,
            "강습 확정",
            "요청 받았던 강습이 확정되었어요. 강습생과 채팅하며 강습을 시작해보세요.",
            "https://ssing.app/instructor-lesson/",
            Target.LESSON_ID,
            Target.LESSON_ID,
            Target.LESSON_ID
    );

    private final NotificationType type;
    private final ClientApp clientApp;
    private final String title;
    private final String body;
    private final String deepLinkBase;
    private final Target deepLinkTarget;
    private final Target fcmTarget;
    private final Target storedTarget;

    NotificationDefinition(
            NotificationType type,
            ClientApp clientApp,
            String title,
            String body,
            String deepLinkBase,
            Target deepLinkTarget,
            Target fcmTarget,
            Target storedTarget
    ) {
        this.type = type;
        this.clientApp = clientApp;
        this.title = title;
        this.body = body;
        this.deepLinkBase = deepLinkBase;
        this.deepLinkTarget = deepLinkTarget;
        this.fcmTarget = fcmTarget;
        this.storedTarget = storedTarget;
    }

    public NotificationType type() {
        return type;
    }

    public ClientApp clientApp() {
        return clientApp;
    }

    public String title() {
        return title;
    }

    public String body() {
        return body;
    }

    public Target fcmTarget() {
        return fcmTarget;
    }

    public Target storedTarget() {
        return storedTarget;
    }

    public String deepLink(Long targetId) {
        return deepLinkTarget == Target.NONE ? deepLinkBase : deepLinkBase + targetId;
    }

    public enum Target {
        NONE(null),
        OFFER_ID("offerId"),
        LESSON_ID("lessonId");

        private final String dataKey;

        Target(String dataKey) {
            this.dataKey = dataKey;
        }

        public String dataKey() {
            return dataKey;
        }
    }
}
