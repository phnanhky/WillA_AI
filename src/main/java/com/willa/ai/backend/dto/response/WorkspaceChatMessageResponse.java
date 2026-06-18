package com.willa.ai.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class WorkspaceChatMessageResponse {
    private Long id;
    private Long channelId;
    private Long conversationId;
    private Long userId;
    private String userName;
    private String content;
    private LocalDateTime createdAt;
}
