package com.willa.ai.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class BrandKitReferenceImageResponse {
    private Long id;
    private String imageUrl;
    private String fileName;
    private Long fileSizeBytes;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
