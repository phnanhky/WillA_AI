package com.willa.ai.backend.entity;

import com.willa.ai.backend.entity.enums.TaskDeadlineNotificationType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "task_deadline_notifications",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_task_deadline_notification",
                columnNames = {"task_id", "notification_type", "due_date"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskDeadlineNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 20)
    private TaskDeadlineNotificationType notificationType;

    @Column(name = "due_date", nullable = false)
    private LocalDateTime dueDate;

    @CreationTimestamp
    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;
}
