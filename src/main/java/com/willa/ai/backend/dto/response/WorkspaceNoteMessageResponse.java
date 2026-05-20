package com.willa.ai.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class WorkspaceNoteMessageResponse {
    private Long id;
    private Long workspaceId;
    private Long userId;
    private String userName;
    private String userEmail;
    private String content;
    private LocalDateTime createdAt;
}
