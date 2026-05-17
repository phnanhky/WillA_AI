package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.WorkspacePage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspacePageRepository extends JpaRepository<WorkspacePage, Long> {
    List<WorkspacePage> findByWorkspaceIdOrderByPageNumberAsc(Long workspaceId);
    Optional<WorkspacePage> findByWorkspaceIdAndPageNumber(Long workspaceId, Integer pageNumber);
    void deleteByWorkspaceId(Long workspaceId);
}
