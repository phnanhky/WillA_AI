package com.willa.ai.backend.dto.response;

import com.willa.ai.backend.entity.enums.ChecklistPriority;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class TaskChecklistResponse {
    private Long id;
    private Long taskId;
    private String title;
    private Integer position;
    private List<TaskChecklistItemResponse> items;
}
