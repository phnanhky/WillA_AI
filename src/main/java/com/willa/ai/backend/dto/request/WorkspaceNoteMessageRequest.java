package com.willa.ai.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkspaceNoteMessageRequest {
    @NotBlank(message = "Nội dung ghi chú không được để trống")
    private String content;
}
