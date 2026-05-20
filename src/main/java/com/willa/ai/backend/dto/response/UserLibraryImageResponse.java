package com.willa.ai.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserLibraryImageResponse {
    private Long id;
    private String imageUrl;
    private Long fileSizeBytes;
    private LocalDateTime createdAt;
}
