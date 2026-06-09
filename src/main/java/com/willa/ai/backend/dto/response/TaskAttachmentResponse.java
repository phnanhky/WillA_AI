package com.willa.ai.backend.dto.response;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskAttachmentResponse {
    private Long id;
    private String fileName;
    private String fileUrl;
    private String fileType;
    private Long uploadedBy;
    private String uploadedByName;
    private LocalDateTime uploadedAt;
}
