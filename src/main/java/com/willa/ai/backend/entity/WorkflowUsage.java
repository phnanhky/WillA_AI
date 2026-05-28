package com.willa.ai.backend.entity;

import com.willa.ai.backend.entity.enums.WorkflowType;
import com.willa.ai.backend.entity.enums.WorkflowUsageStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "workflow_usages", indexes = {
        @Index(name = "idx_workflow_usage_user_started", columnList = "user_id, started_at"),
        @Index(name = "idx_workflow_usage_workflow_started", columnList = "workflow, started_at"),
        @Index(name = "idx_workflow_usage_session", columnList = "chat_session_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow", nullable = false, length = 40)
    private WorkflowType workflow;

    @Column(name = "chat_session_id")
    private Long chatSessionId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at", nullable = false)
    private LocalDateTime endedAt;

    @Column(name = "duration_ms", nullable = false)
    private Long durationMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WorkflowUsageStatus status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;
}
