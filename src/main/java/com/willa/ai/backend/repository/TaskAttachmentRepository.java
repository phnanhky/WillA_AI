package com.willa.ai.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.willa.ai.backend.entity.TaskAttachment;

public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, Long> {
    List<TaskAttachment> findByTaskIdOrderByUploadedAtDesc(Long taskId);
}
