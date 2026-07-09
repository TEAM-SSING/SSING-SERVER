package org.sopt.ssingserver.domain.matching.realtime;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroupItem;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferCreatedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingRequestStatusChangedEvent;
import org.sopt.ssingserver.domain.matching.repository.MatchingOfferRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestGroupItemRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MatchingNotificationContextLoader {

    private static final String IMMEDIATE_START_TYPE = "IMMEDIATE";

    private final MatchingOfferRepository matchingOfferRepository;
    private final MatchingRequestRepository matchingRequestRepository;
    private final MatchingRequestGroupItemRepository matchingRequestGroupItemRepository;

    // 커밋 이후 WebSocket 계약 payload 구성에 필요한 강사/요청/그룹 요약을 다시 조회한다.
    @Transactional(readOnly = true)
    public Optional<MatchingOfferReceivedContext> load(MatchingOfferCreatedEvent event) {
        Optional<MatchingOffer> matchingOffer =
                matchingOfferRepository.findRealtimeContextById(event.matchingOfferId());
        if (matchingOffer.isEmpty()) {
            return Optional.empty();
        }

        MatchingOffer offer = matchingOffer.get();
        List<MatchingRequestGroupItem> groupItems =
                matchingRequestGroupItemRepository.findRealtimeContextByMatchingRequestGroupIdOrderByIdAsc(
                        event.matchingRequestGroupId()
                );
        if (groupItems.isEmpty()) {
            return Optional.empty();
        }

        MatchingRequest firstRequest = groupItems.getFirst().getMatchingRequest();
        int totalHeadcount = groupItems.stream()
                .map(MatchingRequestGroupItem::getMatchingRequest)
                .mapToInt(MatchingRequest::getHeadcount)
                .sum();

        return Optional.of(new MatchingOfferReceivedContext(
                offer.getInstructorProfile().getMember().getId(),
                firstRequest.getMember().getNickname(),
                firstRequest.getHeadcount(),
                groupItems.size(),
                firstRequest.getResort().getDisplayName(),
                firstRequest.getSport().name(),
                firstRequest.getLessonLevel().name(),
                event.durationMinutes(),
                totalHeadcount,
                IMMEDIATE_START_TYPE
        ));
    }

    // 상태 변경 이벤트 수신자인 소비자 memberId를 DB에서 다시 확인한다.
    @Transactional(readOnly = true)
    public Optional<MatchingStatusChangedContext> load(MatchingRequestStatusChangedEvent event) {
        return matchingRequestRepository.findRealtimeStatusContextById(event.matchingRequestId())
                .map(matchingRequest -> new MatchingStatusChangedContext(matchingRequest.getMember().getId()));
    }

    public record MatchingOfferReceivedContext(
            Long recipientMemberId,
            String requesterName,
            int headcount,
            int matchingRequestCount,
            String resortName,
            String sport,
            String level,
            int durationMinutes,
            int totalHeadcount,
            String startType
    ) {
    }

    public record MatchingStatusChangedContext(
            Long recipientMemberId
    ) {
    }
}
