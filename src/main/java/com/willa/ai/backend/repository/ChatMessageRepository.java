package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.ChatMessage;
import com.willa.ai.backend.entity.enums.MessageRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    Page<ChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId, Pageable pageable);
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    @Query("""
            SELECT m FROM ChatMessage m
            JOIN m.session s
            WHERE s.user.id = :userId
              AND m.role = :role
              AND m.content IS NOT NULL
              AND m.content <> ''
              AND (
                m.content LIKE '%"type":"analysis"%'
                OR m.content LIKE '%"type": "analysis"%'
              )
            ORDER BY m.createdAt DESC
            """)
    List<ChatMessage> findRecentAnalysisMessagesByUserId(
            @Param("userId") Long userId,
            @Param("role") MessageRole role,
            Pageable pageable);
}
