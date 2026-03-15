package com.willa.ai.backend.dto.response;

import com.willa.ai.backend.entity.enums.MessageRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {
    private Long id;
    private Long sessionId;
    private MessageRole role;
    private String content;
    private String imageUrl;
    private Integer tokensUsed;
    private LocalDateTime createdAt;
}
