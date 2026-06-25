package com.willa.ai.backend.dto.response;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskCommentResponse {
    private Long id;
    private Long taskId;
    private Long userId;
    private String userName;
    private String userAvatarUrl;
    private String content;
    private Long parentCommentId;
    private Long parentUserId;
    private String parentUserName;
    private String taskTitle;
    private LocalDateTime createdAt;
}
