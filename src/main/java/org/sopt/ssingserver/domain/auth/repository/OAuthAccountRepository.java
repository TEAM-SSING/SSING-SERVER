package org.sopt.ssingserver.domain.auth.repository;

import java.util.Optional;
import org.sopt.ssingserver.domain.auth.entity.OAuthAccount;
import org.sopt.ssingserver.domain.auth.enums.OAuthProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, Long> {

    Optional<OAuthAccount> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);

    @EntityGraph(attributePaths = "member")
    Page<OAuthAccount> findAllByProviderOrderByIdDesc(OAuthProvider provider, Pageable pageable);

    @EntityGraph(attributePaths = "member")
    Optional<OAuthAccount> findByMemberIdAndProvider(Long memberId, OAuthProvider provider);
}
