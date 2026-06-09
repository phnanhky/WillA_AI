package com.willa.ai.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.willa.ai.backend.entity.Task;
import com.willa.ai.backend.entity.enums.TaskStatus;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByWorkspaceIdOrderByPositionAscIdAsc(Long workspaceId);

    List<Task> findByWorkspaceIdAndStatusOrderByPositionAscIdAsc(Long workspaceId, TaskStatus status);

    Optional<Task> findByIdAndWorkspaceId(Long id, Long workspaceId);

    @Query("""
            SELECT t FROM Task t
            WHERE t.status <> com.willa.ai.backend.entity.enums.TaskStatus.DONE
              AND t.dueDate IS NOT NULL
              AND t.dueDate BETWEEN :from AND :to
            """)
    List<Task> findUpcomingDueTasks(
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to);
}
