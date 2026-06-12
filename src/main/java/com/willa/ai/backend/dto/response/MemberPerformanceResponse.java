package com.willa.ai.backend.dto.response;

import com.willa.ai.backend.entity.enums.WorkspaceRole;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MemberPerformanceResponse {
    private Long userId;
    private String userName;
    private String email;
    private WorkspaceRole role;
    private int totalAssigned;
    private int completedCount;
    private double completionRate;
    private int dueTotal;
    private int onTimeCount;
    private int lateCount;
    private double onTimeRate;
    private double lateRate;
}
