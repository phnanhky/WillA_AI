package com.willa.ai.backend.dto.request;

import com.willa.ai.backend.entity.enums.ChannelMessageKind;
import lombok.Data;

@Data
public class WorkspaceChatMessageRequest {
    private String content;

    /** Tin nhắn trong thread — trỏ tới tin gốc trên kênh */
    private Long parentMessageId;

    private ChannelMessageKind kind;

    private String imageUrl;

    /** JSON serialized ToolResult (WillA) */
    private String toolResultJson;
}
