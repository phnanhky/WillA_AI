package com.willa.ai.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TaskAttachmentUpdateRequest {
    @NotBlank(message = "Tên tệp không được để trống")
    private String fileName;
}
