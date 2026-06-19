package com.willa.ai.backend.dto.response;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkspaceProjectResponse {
    private Long id;
    private Long workspaceId;
    private String name;
    private Integer position;
    private Long channelId;
    private Integer taskCount;
    private LocalDateTime createdAt;
}
