package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.WorkspaceExpert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceExpertRepository extends JpaRepository<WorkspaceExpert, Long> {

    List<WorkspaceExpert> findAllByOrderByCreatedAtDesc();

    List<WorkspaceExpert> findByWorkspaceIdAndIsActiveTrueOrderByCreatedAtDesc(Long workspaceId);

    List<WorkspaceExpert> findByIsActiveTrueOrderByCreatedAtDesc();

    boolean existsByUserId(Long userId);

    Optional<WorkspaceExpert> findByUserId(Long userId);

    Optional<WorkspaceExpert> findByWorkspaceIdAndUserId(Long workspaceId, Long userId);

    Optional<WorkspaceExpert> findByWorkspaceIsNullAndUserId(Long userId);

    boolean existsByWorkspaceIdAndUserId(Long workspaceId, Long userId);

    boolean existsByWorkspaceIsNullAndUserId(Long userId);
}
