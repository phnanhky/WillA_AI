package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.AITokenUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AITokenUsageRepository extends JpaRepository<AITokenUsage, Long> {
}
