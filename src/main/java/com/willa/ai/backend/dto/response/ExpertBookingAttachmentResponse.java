package com.willa.ai.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpertBookingAttachmentResponse {

    private Long id;
    private String fileName;
    private String fileUrl;
    private Long fileSizeBytes;
    private String contentType;
    private LocalDateTime createdAt;
}
