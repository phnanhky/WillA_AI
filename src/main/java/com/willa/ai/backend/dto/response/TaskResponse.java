package com.willa.ai.backend.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.willa.ai.backend.entity.enums.ChecklistPriority;
import com.willa.ai.backend.entity.enums.TaskStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskResponse {
    private Long id;
    private Long workspaceId;
    private Long projectId;
    private String title;
    private String description;
    private TaskStatus status;
    private LocalDateTime dueDate;
    private Integer position;
    private List<TaskAssigneeResponse> assignees;
    private List<TaskAttachmentResponse> attachments;
    private String meetLink;
    private ChecklistPriority labelPriority;
    private Boolean completed;
    private LocalDateTime completedAt;
    private int commentCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
