package org.sopt.ssingserver.domain.notification.service;

import java.time.Clock;
import java.time.Instant;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.repository.MemberRepository;
import org.sopt.ssingserver.domain.notification.dto.request.DeleteFcmTokenRequest;
import org.sopt.ssingserver.domain.notification.dto.request.RegisterFcmTokenRequest;
import org.sopt.ssingserver.domain.notification.entity.FcmToken;
import org.sopt.ssingserver.domain.notification.repository.FcmTokenRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class FcmTokenService {

    private final FcmTokenRepository fcmTokenRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public FcmTokenService(
            FcmTokenRepository fcmTokenRepository,
            MemberRepository memberRepository,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.fcmTokenRepository = fcmTokenRepository;
        this.memberRepository = memberRepository;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void registerOrUpdate(Long memberId, RegisterFcmTokenRequest request) {
        try {
            transactionTemplate.executeWithoutResult(
                    status -> registerOrUpdateInTransaction(memberId, request)
            );
        } catch (DataIntegrityViolationException exception) {
            updateAfterConflict(memberId, request, exception);
        }
    }

    @Transactional
    public void unregister(Long memberId, DeleteFcmTokenRequest request) {
        fcmTokenRepository.deleteByMemberIdAndToken(memberId, request.fcmToken());
    }

    // 단일 트랜잭션에서 토큰 존재 여부에 따라 등록 정보를 생성하거나 수정
    private void registerOrUpdateInTransaction(Long memberId, RegisterFcmTokenRequest request) {
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

    // 무결성 오류로 실패한 트랜잭션과 분리하여 동일 토큰이 존재하면 등록 정보를 수정
    private void updateAfterConflict(
            Long memberId,
            RegisterFcmTokenRequest request,
            DataIntegrityViolationException originalException
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            Member member = memberRepository.getReferenceById(memberId);
            FcmToken fcmToken = fcmTokenRepository.findByToken(request.fcmToken())
                    .orElseThrow(() -> originalException);

            fcmToken.updateRegistration(
                    member,
                    request.clientApp(),
                    request.platform(),
                    clock.instant()
            );
        });
    }
}
