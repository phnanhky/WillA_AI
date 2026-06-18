package com.willa.ai.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class WorkspaceChannelResponse {
    private Long id;
    private Long workspaceId;
    private String name;
    private Integer position;
    private Boolean isSystem;
    private Long messageCount;
    private LocalDateTime createdAt;
}
