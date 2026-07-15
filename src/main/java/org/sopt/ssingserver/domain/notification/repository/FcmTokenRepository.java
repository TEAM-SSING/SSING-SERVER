package org.sopt.ssingserver.domain.notification.repository;

import java.util.List;
import java.util.Optional;
import org.sopt.ssingserver.domain.notification.entity.FcmToken;
import org.sopt.ssingserver.domain.notification.enums.ClientApp;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    Optional<FcmToken> findByToken(String token);

    List<FcmToken> findAllByMemberIdAndClientApp(Long memberId, ClientApp clientApp);

    void deleteByMemberIdAndToken(Long memberId, String token);

    void deleteByToken(String token);
}
