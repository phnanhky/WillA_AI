package com.willa.ai.backend.dto.request;

import com.willa.ai.backend.entity.enums.MessageRole;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageRequest {
    @NotNull(message = "Role is required")
    private MessageRole role;

    private String content;

    private String imageUrl;

    private Integer tokensUsed;

    // Optional fields for AI Unified endpoint
    private String actionType;
    private Integer errorIndex;
}
