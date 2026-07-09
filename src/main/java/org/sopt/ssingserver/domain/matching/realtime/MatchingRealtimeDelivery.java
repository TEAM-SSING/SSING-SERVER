package org.sopt.ssingserver.domain.matching.realtime;

import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent;

// 이벤트 내용과 실제 수신자 memberId를 함께 넘겨 Notifier가 destination만 책임지게 한다.
public record MatchingRealtimeDelivery(
        Long recipientMemberId,
        MatchingRealtimeEvent event
) {
}
