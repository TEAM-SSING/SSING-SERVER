package org.sopt.ssingserver.domain.auth.dev.repository;

import java.util.List;
import java.util.Optional;
import org.sopt.ssingserver.domain.auth.dev.entity.DevPersona;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DevPersonaRepository extends JpaRepository<DevPersona, Long> {

    @EntityGraph(attributePaths = "member")
    List<DevPersona> findAllByOrderByCreatedAtAsc();

    boolean existsByPersonaKey(String personaKey);

    Optional<DevPersona> findByPersonaKey(String personaKey);
}
