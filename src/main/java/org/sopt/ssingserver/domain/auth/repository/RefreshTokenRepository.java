package org.sopt.ssingserver.domain.auth.repository;

import java.util.Optional;
import org.sopt.ssingserver.domain.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);
}
