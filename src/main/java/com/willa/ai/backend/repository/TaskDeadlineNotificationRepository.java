package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.TaskDeadlineNotification;
import com.willa.ai.backend.entity.enums.TaskDeadlineNotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface TaskDeadlineNotificationRepository extends JpaRepository<TaskDeadlineNotification, Long> {
    boolean existsByTaskIdAndNotificationTypeAndDueDate(
            Long taskId,
            TaskDeadlineNotificationType notificationType,
            LocalDateTime dueDate);
}
