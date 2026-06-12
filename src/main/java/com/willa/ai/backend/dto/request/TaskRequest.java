package com.willa.ai.backend.dto.request;

import java.time.LocalDateTime;
import java.util.List;

import com.willa.ai.backend.entity.enums.ChecklistPriority;
import com.willa.ai.backend.entity.enums.TaskStatus;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TaskRequest {
    @NotBlank
    private String title;
    private String description;
    private TaskStatus status;
    private LocalDateTime dueDate;
    private Integer position;
    private List<Long> assigneeUserIds;
    /** Gán / xóa link Meet (null = không đổi khi update nếu client không gửi field) */
    private String meetLink;
    private ChecklistPriority labelPriority;
    private Boolean clearLabelPriority;
    private Boolean completed;
}
