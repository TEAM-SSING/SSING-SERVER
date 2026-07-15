package org.sopt.ssingserver.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.member.repository.MemberRepository;
import org.sopt.ssingserver.domain.notification.dto.request.DeleteFcmTokenRequest;
import org.sopt.ssingserver.domain.notification.dto.request.RegisterFcmTokenRequest;
import org.sopt.ssingserver.domain.notification.entity.FcmToken;
import org.sopt.ssingserver.domain.notification.enums.ClientApp;
import org.sopt.ssingserver.domain.notification.enums.ClientPlatform;
import org.sopt.ssingserver.domain.notification.repository.FcmTokenRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

@ExtendWith(MockitoExtension.class)
class FcmTokenServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final String FCM_TOKEN = "same-fcm-token";
    private static final Instant NOW = Instant.parse("2026-07-13T00:00:00Z");
    private static final Instant PREVIOUS_REGISTERED_AT = NOW.minusSeconds(3600);
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private FcmTokenRepository fcmTokenRepository;

    @Mock
    private MemberRepository memberRepository;

    private FcmTokenService fcmTokenService;

    @BeforeEach
    void setUp() {
        fcmTokenService = new FcmTokenService(
                fcmTokenRepository,
                memberRepository,
                FIXED_CLOCK,
                new NoOpTransactionManager()
        );
    }

    @Test
    void registerOrUpdate는_없는_Token이면_현재_회원과_클라이언트정보로_생성한다() {
        Member member = activeMember("소비자", MemberRole.CONSUMER);
        RegisterFcmTokenRequest request = registerRequest(ClientApp.CONSUMER, ClientPlatform.ANDROID);
        when(memberRepository.getReferenceById(MEMBER_ID)).thenReturn(member);
        when(fcmTokenRepository.findByToken(FCM_TOKEN)).thenReturn(Optional.empty());

        fcmTokenService.registerOrUpdate(MEMBER_ID, request);

        ArgumentCaptor<FcmToken> tokenCaptor = ArgumentCaptor.forClass(FcmToken.class);
        verify(fcmTokenRepository).save(tokenCaptor.capture());
        FcmToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.getMember()).isSameAs(member);
        assertThat(savedToken.getClientApp()).isSameAs(ClientApp.CONSUMER);
        assertThat(savedToken.getPlatform()).isSameAs(ClientPlatform.ANDROID);
        assertThat(savedToken.getToken()).isEqualTo(FCM_TOKEN);
        assertThat(savedToken.getLastRegisteredAt()).isEqualTo(NOW);
    }

    @Test
    void registerOrUpdate는_기존_Token이면_Row를_추가하지_않고_등록정보를_갱신한다() {
        Member previousMember = activeMember("기존 회원", MemberRole.CONSUMER);
        Member currentMember = activeMember("현재 강사", MemberRole.INSTRUCTOR);
        FcmToken existingToken = FcmToken.create(
                previousMember,
                ClientApp.CONSUMER,
                ClientPlatform.ANDROID,
                FCM_TOKEN,
                PREVIOUS_REGISTERED_AT
        );
        RegisterFcmTokenRequest request = registerRequest(ClientApp.INSTRUCTOR, ClientPlatform.IOS);
        when(memberRepository.getReferenceById(MEMBER_ID)).thenReturn(currentMember);
        when(fcmTokenRepository.findByToken(FCM_TOKEN)).thenReturn(Optional.of(existingToken));

        fcmTokenService.registerOrUpdate(MEMBER_ID, request);

        assertThat(existingToken.getMember()).isSameAs(currentMember);
        assertThat(existingToken.getClientApp()).isSameAs(ClientApp.INSTRUCTOR);
        assertThat(existingToken.getPlatform()).isSameAs(ClientPlatform.IOS);
        assertThat(existingToken.getToken()).isEqualTo(FCM_TOKEN);
        assertThat(existingToken.getLastRegisteredAt()).isEqualTo(NOW);
        verify(fcmTokenRepository, never()).save(any(FcmToken.class));
    }

    @Test
    void registerOrUpdate는_동시등록_unique_충돌이면_이미_생성된_Token_정보를_갱신한다() {
        Member previousMember = activeMember("기존 회원", MemberRole.CONSUMER);
        Member currentMember = activeMember("현재 강사", MemberRole.INSTRUCTOR);
        FcmToken concurrentToken = FcmToken.create(
                previousMember,
                ClientApp.CONSUMER,
                ClientPlatform.ANDROID,
                FCM_TOKEN,
                PREVIOUS_REGISTERED_AT
        );
        RegisterFcmTokenRequest request = registerRequest(ClientApp.INSTRUCTOR, ClientPlatform.IOS);
        when(memberRepository.getReferenceById(MEMBER_ID)).thenReturn(currentMember);
        when(fcmTokenRepository.findByToken(FCM_TOKEN))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(concurrentToken));
        when(fcmTokenRepository.save(any(FcmToken.class)))
                .thenThrow(new DataIntegrityViolationException("uk_fcm_tokens_token"));

        fcmTokenService.registerOrUpdate(MEMBER_ID, request);

        assertThat(concurrentToken.getMember()).isSameAs(currentMember);
        assertThat(concurrentToken.getClientApp()).isSameAs(ClientApp.INSTRUCTOR);
        assertThat(concurrentToken.getPlatform()).isSameAs(ClientPlatform.IOS);
        assertThat(concurrentToken.getToken()).isEqualTo(FCM_TOKEN);
        assertThat(concurrentToken.getLastRegisteredAt()).isEqualTo(NOW);
        verify(fcmTokenRepository, times(2)).findByToken(FCM_TOKEN);
    }

    @Test
    void registerOrUpdate는_저장충돌후에도_Token이_없으면_원래예외를_전파한다() {
        Member currentMember = activeMember("현재 회원", MemberRole.CONSUMER);
        RegisterFcmTokenRequest request = registerRequest(ClientApp.CONSUMER, ClientPlatform.ANDROID);
        DataIntegrityViolationException originalException =
                new DataIntegrityViolationException("uk_fcm_tokens_token");
        when(memberRepository.getReferenceById(MEMBER_ID)).thenReturn(currentMember);
        when(fcmTokenRepository.findByToken(FCM_TOKEN))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(fcmTokenRepository.save(any(FcmToken.class))).thenThrow(originalException);

        assertThatThrownBy(() -> fcmTokenService.registerOrUpdate(MEMBER_ID, request))
                .isSameAs(originalException);
    }

    @Test
    void unregister는_현재_회원과_Token이_모두_일치하는_Row만_삭제한다() {
        DeleteFcmTokenRequest request = new DeleteFcmTokenRequest(FCM_TOKEN);

        fcmTokenService.unregister(MEMBER_ID, request);

        verify(fcmTokenRepository).deleteByMemberIdAndToken(MEMBER_ID, FCM_TOKEN);
    }

    @Test
    void removeInvalidToken은_afterCommit에서도_삭제를_커밋하도록_독립_트랜잭션을_사용한다()
            throws NoSuchMethodException {
        Transactional transactional = FcmTokenService.class
                .getDeclaredMethod("removeInvalidToken", String.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    private RegisterFcmTokenRequest registerRequest(ClientApp clientApp, ClientPlatform platform) {
        return new RegisterFcmTokenRequest(clientApp, platform, FCM_TOKEN);
    }

    private Member activeMember(String nickname, MemberRole role) {
        return Member.create(nickname, null, role, MemberStatus.ACTIVE);
    }

    // TransactionTemplate 콜백 경계만 실행하는 단위 테스트용 더미다.
    private static class NoOpTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
