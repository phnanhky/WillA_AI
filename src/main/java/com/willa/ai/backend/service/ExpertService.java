package com.willa.ai.backend.service;

import com.willa.ai.backend.dto.request.WorkspaceExpertRequest;
import com.willa.ai.backend.dto.response.AdminWorkspaceSummaryResponse;
import com.willa.ai.backend.dto.response.WorkspaceExpertResponse;

import java.util.List;

public interface ExpertService {

    List<WorkspaceExpertResponse> listAllExperts();

    List<WorkspaceExpertResponse> listActiveExperts();

    List<WorkspaceExpertResponse> listPlatformExperts();

    List<WorkspaceExpertResponse> listWorkspaceExperts(String currentEmail, Long workspaceId);

    List<AdminWorkspaceSummaryResponse> listAllWorkspacesForAdmin();

    WorkspaceExpertResponse createExpert(WorkspaceExpertRequest request);

    WorkspaceExpertResponse updateExpert(Long expertId, WorkspaceExpertRequest request);

    void deleteExpert(Long expertId);
}
