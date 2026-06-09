package com.willa.ai.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.willa.ai.backend.entity.TaskComment;

public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {
    List<TaskComment> findByTaskIdOrderByCreatedAtAsc(Long taskId);
}
