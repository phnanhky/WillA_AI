package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    Page<ChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId, Pageable pageable);
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
}
