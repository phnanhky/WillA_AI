package com.willa.ai.backend.dto.request;

import com.willa.ai.backend.entity.enums.ChecklistPriority;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TaskChecklistItemRequest {
    private String title;
    private Boolean completed;
    private LocalDateTime dueDate;
    private Boolean clearDueDate;
    private Long assigneeUserId;
    private Boolean clearAssignee;
    private ChecklistPriority priority;
}
