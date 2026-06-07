package com.willa.ai.backend.dto.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GalleryItemResponse {
    private String id;
    private String url;
    private String sessionId;
    private String resultMessageId;
    private String sessionTitle;
    private String description;
    private Instant createdAt;
}
