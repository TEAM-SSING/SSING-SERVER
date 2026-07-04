package org.sopt.ssingserver.domain.notification.service;

import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.repository.MemberRepository;
import org.sopt.ssingserver.domain.notification.dto.request.RegisterFcmTokenRequest;
import org.sopt.ssingserver.domain.notification.entity.FcmToken;
import org.sopt.ssingserver.domain.notification.repository.FcmTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FcmTokenService {

    private final FcmTokenRepository fcmTokenRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;

    @Transactional
    public void registerOrUpdate(
            Long memberId,
            RegisterFcmTokenRequest request
    ) {
        Member member = memberRepository.getReferenceById(memberId);
        Instant registeredAt = clock.instant();

        fcmTokenRepository.findByToken(request.fcmToken())
                .ifPresentOrElse(
                        fcmToken -> fcmToken.updateRegistration(
                                member,
                                request.clientApp(),
                                request.platform(),
                                registeredAt
                        ),
                        () -> fcmTokenRepository.save(FcmToken.create(
                                member,
                                request.clientApp(),
                                request.platform(),
                                request.fcmToken(),
                                registeredAt
                        ))
                );
    }
}
