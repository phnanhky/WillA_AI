package com.willa.ai.backend.dto.request;

import com.willa.ai.backend.entity.enums.ChecklistPriority;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TaskChecklistItemRequest {
    private String title;
    private Boolean completed;
    private LocalDateTime dueDate;
    private Boolean clearDueDate;
    private Long assigneeUserId;
    private Boolean clearAssignee;
    private List<Long> assigneeUserIds;
    private Boolean clearAssignees;
    private ChecklistPriority priority;
}
