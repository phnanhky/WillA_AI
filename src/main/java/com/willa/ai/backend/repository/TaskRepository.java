package com.willa.ai.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.willa.ai.backend.entity.Task;
import com.willa.ai.backend.entity.enums.TaskStatus;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByWorkspaceIdOrderByPositionAscIdAsc(Long workspaceId);

    List<Task> findByWorkspaceIdAndProjectIdOrderByPositionAscIdAsc(Long workspaceId, Long projectId);

    List<Task> findByWorkspaceIdAndStatusOrderByPositionAscIdAsc(Long workspaceId, TaskStatus status);

    List<Task> findByWorkspaceIdAndProjectIdAndStatusOrderByPositionAscIdAsc(
            Long workspaceId, Long projectId, TaskStatus status);

    Optional<Task> findByIdAndWorkspaceId(Long id, Long workspaceId);

    long countByWorkspaceIdAndProjectId(Long workspaceId, Long projectId);

    @Query("""
            SELECT DISTINCT t FROM Task t
            LEFT JOIN FETCH t.assignees
            WHERE t.workspace.id = :workspaceId
            ORDER BY t.position ASC, t.id ASC
            """)
    List<Task> findByWorkspaceIdWithAssignees(@Param("workspaceId") Long workspaceId);

    @Modifying
    @Query(value = "DELETE FROM tasks WHERE workspace_id = :workspaceId", nativeQuery = true)
    void deleteByWorkspaceId(@Param("workspaceId") Long workspaceId);

    @Query("""
            SELECT t FROM Task t
            WHERE t.status <> com.willa.ai.backend.entity.enums.TaskStatus.DONE
              AND t.dueDate IS NOT NULL
              AND t.dueDate BETWEEN :from AND :to
            """)
    List<Task> findUpcomingDueTasks(
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to);

    @Query("""
            SELECT DISTINCT t FROM Task t
            LEFT JOIN FETCH t.assignees
            LEFT JOIN FETCH t.workspace
            LEFT JOIN FETCH t.project
            WHERE t.status <> com.willa.ai.backend.entity.enums.TaskStatus.DONE
              AND t.dueDate IS NOT NULL
              AND t.dueDate >= :from
              AND t.dueDate < :to
            """)
    List<Task> findDueTasksInWindow(
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to);
}
