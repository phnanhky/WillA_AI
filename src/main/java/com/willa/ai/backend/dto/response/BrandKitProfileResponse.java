package com.willa.ai.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class BrandKitProfileResponse {
    private Long id;
    private String title;
    private Long workspaceId;
    private Map<String, Object> visualDna;
    private List<BrandKitReferenceImageResponse> referenceImages;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastCheckedAt;
}
