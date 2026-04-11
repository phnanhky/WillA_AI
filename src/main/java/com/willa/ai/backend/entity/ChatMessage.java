package com.willa.ai.backend.entity;

import com.willa.ai.backend.entity.enums.MessageRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_chat_message_session_id", columnList = "session_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageRole role; // USER, AI, SYSTEM

    @Column(columnDefinition = "TEXT")
    private String content; // Có thể lưu prompt hoặc response text

    private String imageUrl; // Cho image upload hoặc generated image

    @Builder.Default
    private Integer tokensUsed = 0; // Trừ wallet dựa trên cái này

    @CreationTimestamp
    private LocalDateTime createdAt;
}
