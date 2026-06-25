package com.willa.ai.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.willa.ai.backend.entity.TaskComment;

public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {
    List<TaskComment> findByTaskIdOrderByCreatedAtAsc(Long taskId);

    @Query("""
            SELECT DISTINCT c FROM TaskComment c
            JOIN FETCH c.task t
            JOIN FETCH c.user u
            LEFT JOIN FETCH c.parentComment pc
            LEFT JOIN FETCH pc.user pu
            WHERE t.workspace.id = :workspaceId
            AND (
              EXISTS (SELECT 1 FROM t.assignees a WHERE a.id = :userId)
              OR c.user.id = :userId
              OR (pc IS NOT NULL AND pc.user.id = :userId)
            )
            ORDER BY c.createdAt DESC
            """)
    List<TaskComment> findHubActivity(
            @Param("workspaceId") Long workspaceId,
            @Param("userId") Long userId);
}
