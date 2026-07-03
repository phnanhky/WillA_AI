package com.willa.ai.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkspaceKnowledgeAIRequest {
    @NotBlank(message = "Question cannot be blank")
    private String question;
}
