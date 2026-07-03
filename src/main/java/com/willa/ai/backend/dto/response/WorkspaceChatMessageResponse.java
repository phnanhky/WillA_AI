package com.willa.ai.backend.dto.response;

import com.willa.ai.backend.entity.enums.ChannelMessageKind;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class WorkspaceChatMessageResponse {
    private Long id;
    private Long channelId;
    private Long conversationId;
    private Long parentMessageId;
    private ChannelMessageKind kind;
    private Long userId;
    private String userName;
    private String userAvatarUrl;
    private String content;
    private String imageUrl;
    private String toolResultJson;
    private LocalDateTime createdAt;
}
