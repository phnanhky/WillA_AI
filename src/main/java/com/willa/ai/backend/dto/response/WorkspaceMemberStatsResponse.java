package com.willa.ai.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class WorkspaceMemberStatsResponse {
    private Long workspaceId;
    private Long viewerUserId;
    private boolean ownerView;
    private List<MemberPerformanceResponse> members;
}
