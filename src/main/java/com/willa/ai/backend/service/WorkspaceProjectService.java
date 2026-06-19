package com.willa.ai.backend.service;

import java.util.List;

import com.willa.ai.backend.dto.request.WorkspaceProjectRequest;
import com.willa.ai.backend.dto.response.WorkspaceProjectResponse;

public interface WorkspaceProjectService {
    List<WorkspaceProjectResponse> listProjects(String email, Long workspaceId);

    WorkspaceProjectResponse createProject(String email, Long workspaceId, WorkspaceProjectRequest request);

    void deleteProject(String email, Long workspaceId, Long projectId);
}
