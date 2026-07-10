package com.willa.ai.backend.cron;

import com.willa.ai.backend.entity.Task;
import com.willa.ai.backend.entity.TaskDeadlineNotification;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.enums.TaskDeadlineNotificationType;
import com.willa.ai.backend.repository.TaskDeadlineNotificationRepository;
import com.willa.ai.backend.repository.TaskRepository;
import com.willa.ai.backend.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskDeadlineCronTask {

    private static final DateTimeFormatter DUE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final TaskRepository taskRepository;
    private final TaskDeadlineNotificationRepository notificationRepository;
    private final EmailService emailService;

    @Value("${app.frontendUrl:http://localhost:5173}")
    private String frontendUrl;

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void sendDeadlineReminderEmails() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowEnd = now.plusMinutes(1);

        sendForWindow(
                now.plusDays(1),
                now.plusDays(1).plusMinutes(1),
                TaskDeadlineNotificationType.ONE_DAY_BEFORE,
                "Nhắc deadline — còn 1 ngày",
                "Deadline reminder — 1 day left"
        );

        sendForWindow(now, windowEnd, TaskDeadlineNotificationType.DUE, "Deadline đến hạn", "Deadline is due now");
    }

    private void sendForWindow(
            LocalDateTime from,
            LocalDateTime to,
            TaskDeadlineNotificationType type,
            String subjectVi,
            String subjectEn) {
        List<Task> tasks = taskRepository.findDueTasksInWindow(from, to);
        if (tasks.isEmpty()) {
            return;
        }

        int sentCount = 0;
        for (Task task : tasks) {
            if (task.getDueDate() == null || task.getAssignees() == null || task.getAssignees().isEmpty()) {
                continue;
            }
            if (notificationRepository.existsByTaskIdAndNotificationTypeAndDueDate(
                    task.getId(), type, task.getDueDate())) {
                continue;
            }

            String workspaceTitle = task.getWorkspace() != null ? task.getWorkspace().getTitle() : "Workspace";
            String dueLabel = task.getDueDate().format(DUE_FORMAT);
            String taskUrl = buildTaskUrl(task);

            for (User assignee : task.getAssignees()) {
                String email = assignee.getEmail();
                if (email == null || email.isBlank()) {
                    continue;
                }
                emailService.sendTaskDeadlineEmail(
                        email,
                        subjectVi,
                        subjectEn,
                        assignee.getFullName(),
                        task.getTitle(),
                        workspaceTitle,
                        dueLabel,
                        type,
                        taskUrl);
                sentCount++;
            }

            notificationRepository.save(TaskDeadlineNotification.builder()
                    .task(task)
                    .notificationType(type)
                    .dueDate(task.getDueDate())
                    .build());
        }

        if (sentCount > 0) {
            log.info("Sent {} deadline {} email(s) for window {} - {}", sentCount, type, from, to);
        }
    }

    private String buildTaskUrl(Task task) {
        String base = frontendUrl.replaceAll("/$", "");
        Long workspaceId = task.getWorkspace() != null ? task.getWorkspace().getId() : null;
        Long projectId = task.getProject() != null ? task.getProject().getId() : null;
        if (workspaceId == null) {
            return base + "/workspace";
        }
        if (projectId != null) {
            return base + "/workspace/" + workspaceId + "?project=" + projectId + "&task=" + task.getId();
        }
        return base + "/workspace/" + workspaceId + "?task=" + task.getId();
    }
}
