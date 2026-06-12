package com.willa.ai.backend.service;

import com.willa.ai.backend.dto.response.MemberPerformanceResponse;
import com.willa.ai.backend.dto.response.WorkspaceMemberStatsResponse;

public interface WorkspaceMemberStatsService {
    WorkspaceMemberStatsResponse getMemberStats(String email, Long workspaceId);
}
