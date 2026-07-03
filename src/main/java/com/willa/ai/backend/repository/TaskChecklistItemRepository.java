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

    @Query("""
            SELECT DISTINCT i FROM TaskChecklistItem i
            LEFT JOIN FETCH i.assignee
            LEFT JOIN FETCH i.assignees
            WHERE i.checklist.id = :checklistId
            ORDER BY i.position ASC, i.id ASC
            """)
    List<TaskChecklistItem> findByChecklistIdWithAssignees(@Param("checklistId") Long checklistId);

    Optional<TaskChecklistItem> findByIdAndChecklistId(Long id, Long checklistId);

    @Query("""
            SELECT i FROM TaskChecklistItem i
            JOIN FETCH i.checklist c
            JOIN FETCH c.task t
            LEFT JOIN FETCH i.assignee
            LEFT JOIN FETCH i.assignees
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
