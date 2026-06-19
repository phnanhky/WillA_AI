package com.willa.ai.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.willa.ai.backend.entity.WorkspaceProject;

public interface WorkspaceProjectRepository extends JpaRepository<WorkspaceProject, Long> {
    List<WorkspaceProject> findByWorkspaceIdOrderByPositionAscIdAsc(Long workspaceId);

    Optional<WorkspaceProject> findByIdAndWorkspaceId(Long id, Long workspaceId);

    boolean existsByWorkspaceIdAndNameIgnoreCase(Long workspaceId, String name);
}
