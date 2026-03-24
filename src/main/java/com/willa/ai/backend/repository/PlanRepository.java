package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.Plan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlanRepository extends JpaRepository<Plan, Long> {
    Page<Plan> findByIsActiveTrue(Pageable pageable);
}
