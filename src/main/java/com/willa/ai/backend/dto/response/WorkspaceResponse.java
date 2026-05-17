package com.willa.ai.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class WorkspaceResponse {
    private Long id;
    private String name;
    private String description;
    private Long ownerId;
    private String ownerName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** URL ảnh trang đầu tiên (thumbnail cho danh sách dự án) */
    private String thumbnailUrl;
    private Long storageUsed;
    private Long maxStorageLimits;
    private Boolean isPublic;
    private Integer likesCount;
    private Integer clonesCount;
}
