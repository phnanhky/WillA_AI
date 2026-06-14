package com.willa.ai.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkspaceChatExtractRequest {
    @NotBlank(message = "Message is required")
    private String message;
    
    @NotBlank(message = "Current date is required")
    private String currentDate;
    
    private String lists;
}
