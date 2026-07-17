package org.sopt.ssingserver.domain.resort.repository;

import java.util.List;
import java.util.Optional;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResortRepository extends JpaRepository<Resort, Long> {

    Optional<Resort> findByCode(String code);

    List<Resort> findAllByOrderByDisplayNameAsc();
}
