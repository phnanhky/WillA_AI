package com.willa.ai.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceKnowledgeAIResponse {
    private String answer;
    private String intent;
    private String modelUsed;
    private Long tokenInput;
}
