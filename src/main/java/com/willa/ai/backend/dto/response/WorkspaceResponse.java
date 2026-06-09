package com.willa.ai.backend.dto.response;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkspaceResponse {
    private Long id;
    private String title;
    private String description;
    private Long ownerId;
    private String ownerName;
    private String inviteCode;
    private String inviteLink;
    private String qrCodeUrl;
    private Boolean isImportant;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
