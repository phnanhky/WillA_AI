package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.UserPersona;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserPersonaRepository extends JpaRepository<UserPersona, Long> {
    Optional<UserPersona> findByUserId(Long userId);
}
