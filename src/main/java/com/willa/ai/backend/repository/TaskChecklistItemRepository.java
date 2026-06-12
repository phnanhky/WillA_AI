package com.willa.ai.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.willa.ai.backend.entity.TaskChecklistItem;

import java.util.List;
import java.util.Set;
import java.util.Optional;

public interface TaskChecklistItemRepository extends JpaRepository<TaskChecklistItem, Long> {
    List<TaskChecklistItem> findByChecklistIdOrderByPositionAscIdAsc(Long checklistId);

    Optional<TaskChecklistItem> findByIdAndChecklistId(Long id, Long checklistId);

    @Query("""
            SELECT i FROM TaskChecklistItem i
            JOIN FETCH i.checklist c
            JOIN FETCH c.task t
            LEFT JOIN FETCH i.assignee
            WHERE t.workspace.id = :workspaceId
            """)
    List<TaskChecklistItem> findByWorkspaceId(@Param("workspaceId") Long workspaceId);

    @Query("""
            SELECT DISTINCT c.task.id FROM TaskChecklistItem i
            JOIN i.checklist c
            JOIN c.task t
            WHERE t.workspace.id = :workspaceId
            """)
    Set<Long> findTaskIdsWithChecklistItemsByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
