package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.WorkspaceDmMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkspaceDmMessageRepository extends JpaRepository<WorkspaceDmMessage, Long> {
    List<WorkspaceDmMessage> findByConversationIdOrderByCreatedAtAscIdAsc(Long conversationId);
}
