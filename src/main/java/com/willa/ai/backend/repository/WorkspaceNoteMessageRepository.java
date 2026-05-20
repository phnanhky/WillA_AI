package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.WorkspaceNoteMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkspaceNoteMessageRepository extends JpaRepository<WorkspaceNoteMessage, Long> {
    List<WorkspaceNoteMessage> findByWorkspaceIdOrderByCreatedAtAsc(Long workspaceId);
    void deleteByWorkspaceId(Long workspaceId);
}
