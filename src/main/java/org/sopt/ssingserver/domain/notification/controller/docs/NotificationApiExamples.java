package org.sopt.ssingserver.domain.notification.controller.docs;

import java.time.Instant;
import java.util.List;
import org.sopt.ssingserver.domain.notification.dto.response.NotificationListResponse;
import org.sopt.ssingserver.domain.notification.dto.response.NotificationListResponse.NotificationItemResponse;
import org.sopt.ssingserver.domain.notification.enums.NotificationType;
import org.sopt.ssingserver.global.response.CommonSuccessCode;
import org.sopt.ssingserver.global.swagger.success.ApiSuccessExampleProvider;
import org.sopt.ssingserver.global.swagger.success.ApiSuccessExampleValue;

final class NotificationApiExamples {

    private NotificationApiExamples() {
    }

    public static final class NotificationList implements ApiSuccessExampleProvider {

        @Override
        public List<ApiSuccessExampleValue> examples() {
            return List.of(
                    ApiSuccessExampleValue.success(
                            "PAGE_WITH_NEXT",
                            "다음 페이지가 있는 알림 목록",
                            CommonSuccessCode.SUCCESS,
                            pageWithNext()
                    ),
                    ApiSuccessExampleValue.success(
                            "LAST_PAGE",
                            "마지막 알림 목록",
                            CommonSuccessCode.SUCCESS,
                            lastPage()
                    )
            );
        }
    }

    private static NotificationListResponse pageWithNext() {
        return new NotificationListResponse(
                List.of(
                        new NotificationItemResponse(
                                100L,
                                NotificationType.MATCHING_OFFER_RECEIVED,
                                "씽 매칭 강습 도착",
                                "새로운 강습이 도착했어요. 강습생 정보를 확인하고 강습을 수락해보세요.",
                                false,
                                Instant.parse("2026-07-04T13:00:00Z")
                        ),
                        new NotificationItemResponse(
                                99L,
                                NotificationType.MATCHING_CONFIRMED,
                                "강습 매칭 확정",
                                "강습 매칭이 확정되었어요.",
                                true,
                                Instant.parse("2026-07-04T12:59:00Z")
                        )
                ),
                "2026-07-04T12:59:00Z_99",
                true
        );
    }

    private static NotificationListResponse lastPage() {
        return new NotificationListResponse(List.of(), null, false);
    }
}
