package org.sopt.ssingserver.domain.notification.service;

import java.time.Clock;
import java.time.Instant;
import org.hibernate.exception.ConstraintViolationException;
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

    private static final String FCM_TOKEN_UNIQUE_CONSTRAINT = "uk_fcm_tokens_token";

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
            if (!isFcmTokenUniqueConflict(exception)) {
                throw exception;
            }
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

    // Unique 충돌로 실패한 트랜잭션과 분리하여 기존 토큰 정보를 수정
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

    // 예외 원인 체인에서 FCM 토큰 Unique 제약 위반 여부를 확인
    private boolean isFcmTokenUniqueConflict(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolationException
                    && FCM_TOKEN_UNIQUE_CONSTRAINT.equals(constraintViolationException.getConstraintName())) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.contains(FCM_TOKEN_UNIQUE_CONSTRAINT)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
