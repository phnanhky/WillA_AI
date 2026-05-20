package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.WorkspaceLibraryImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WorkspaceLibraryImageRepository extends JpaRepository<WorkspaceLibraryImage, Long> {
    @Query("SELECT i FROM WorkspaceLibraryImage i WHERE i.workspace.id = :workspaceId ORDER BY i.createdAt ASC")
    List<WorkspaceLibraryImage> findByWorkspaceIdOrderByCreatedAtAsc(@Param("workspaceId") Long workspaceId);

    @Modifying
    @Query("DELETE FROM WorkspaceLibraryImage i WHERE i.workspace.id = :workspaceId")
    void deleteByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
