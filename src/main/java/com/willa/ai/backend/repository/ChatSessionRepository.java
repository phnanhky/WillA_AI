package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    Page<ChatSession> findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Page<ChatSession> findByUserIdAndIsActiveTrueAndCreatedAtAfterOrderByCreatedAtDesc(Long userId, java.time.LocalDateTime createdAt, Pageable pageable);
    Optional<ChatSession> findByIdAndUserIdAndIsActiveTrue(Long id, Long userId);
}
