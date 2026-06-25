package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.WorkspacePlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspacePlanRepository extends JpaRepository<WorkspacePlan, Long> {

    Optional<WorkspacePlan> findByCode(String code);

    Optional<WorkspacePlan> findByIsDefaultTrue();

    List<WorkspacePlan> findAllByOrderBySortOrderAscNameAsc();

    List<WorkspacePlan> findByIsActiveTrueOrderBySortOrderAscNameAsc();

    boolean existsByCode(String code);

    boolean existsByIsDefaultTrueAndIdNot(Long id);
}
