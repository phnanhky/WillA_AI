package com.willa.ai.backend.dto.response;

import com.willa.ai.backend.entity.enums.ChecklistPriority;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class TaskChecklistItemResponse {
    private Long id;
    private Long checklistId;
    private String title;
    private Boolean completed;
    private LocalDateTime completedAt;
    private Integer position;
    private LocalDateTime dueDate;
    private Long assigneeUserId;
    private String assigneeName;
    private List<TaskAssigneeResponse> assignees;
    private ChecklistPriority priority;
}
