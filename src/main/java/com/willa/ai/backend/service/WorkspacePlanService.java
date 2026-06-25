package com.willa.ai.backend.service;

import com.willa.ai.backend.dto.request.WorkspacePlanRequest;
import com.willa.ai.backend.dto.response.WorkspacePlanResponse;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.WorkspacePlan;

import java.util.List;

public interface WorkspacePlanService {

    List<WorkspacePlanResponse> listAll();

    List<WorkspacePlanResponse> listActive();

    WorkspacePlanResponse getById(Long id);

    WorkspacePlanResponse create(WorkspacePlanRequest request);

    WorkspacePlanResponse update(Long id, WorkspacePlanRequest request);

    void changeStatus(Long id, boolean isActive);

    WorkspacePlan resolveForUser(User user);

    WorkspacePlan getDefaultPlan();

    int maxOwnedWorkspaces(User user);

    int maxMembersPerWorkspace(User user);

    String displayNameForUser(User user);
}
