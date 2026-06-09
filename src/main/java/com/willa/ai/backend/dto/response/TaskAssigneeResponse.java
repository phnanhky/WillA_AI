package com.willa.ai.backend.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskAssigneeResponse {
    private Long userId;
    private String userName;
    private String email;
}
