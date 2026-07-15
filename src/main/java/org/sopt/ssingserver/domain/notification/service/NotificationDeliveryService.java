package org.sopt.ssingserver.domain.notification.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.repository.MemberRepository;
import org.sopt.ssingserver.domain.notification.entity.FcmToken;
import org.sopt.ssingserver.domain.notification.entity.Notification;
import org.sopt.ssingserver.domain.notification.push.PushClient;
import org.sopt.ssingserver.domain.notification.push.PushMessage;
import org.sopt.ssingserver.domain.notification.repository.FcmTokenRepository;
import org.sopt.ssingserver.domain.notification.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class NotificationDeliveryService {

    private final MemberRepository memberRepository;
    private final NotificationRepository notificationRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final PushClient pushClient;
    private final ObjectMapper objectMapper;

    // 매칭 트랜잭션의 afterCommit에서 호출되므로, 알림함 저장은 별도 트랜잭션으로 커밋한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAndSend(Long memberId, NotificationPayload payload) {
        // 수신 회원과 앱 유형으로 토큰을 먼저 모으되, 토큰이 없어도 알림함 저장은 계속한다.
        Member member = memberRepository.getReferenceById(memberId);
        List<String> tokens = fcmTokenRepository.findAllByMemberIdAndClientApp(memberId, payload.clientApp())
                .stream()
                .map(FcmToken::getToken)
                .toList();

        // FCM 성공 여부와 무관하게 알림함에서 확인할 수 있도록 row를 현재 트랜잭션에 저장한다.
        notificationRepository.save(Notification.create(
                member,
                payload.clientApp(),
                payload.type(),
                payload.title(),
                payload.body(),
                toJson(payload.notificationData())
        ));

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // DB 커밋이 실제로 성공한 경우에만 외부 Firebase 호출을 시작한다.
                tokens.forEach(token -> pushClient.send(new PushMessage(token, payload.fcmData())));
            }
        });
    }

    // JSON 컬럼에는 이동에 필요한 대상 정보만 문자열 JSON으로 저장한다.
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Notification data serialization failed.", exception);
        }
    }
}
