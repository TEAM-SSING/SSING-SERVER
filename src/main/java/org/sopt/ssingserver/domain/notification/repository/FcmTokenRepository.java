package org.sopt.ssingserver.domain.notification.repository;

import java.util.Optional;
import org.sopt.ssingserver.domain.notification.entity.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    Optional<FcmToken> findByToken(String token);
}
