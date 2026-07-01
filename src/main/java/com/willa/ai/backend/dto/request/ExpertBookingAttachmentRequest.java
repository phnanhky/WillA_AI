package com.willa.ai.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpertBookingAttachmentRequest {

    private String fileName;
    private String fileUrl;
    private Long fileSizeBytes;
    private String contentType;
}
