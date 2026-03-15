package com.willa.ai.backend.service;

import com.willa.ai.backend.dto.request.PlanRequest;
import com.willa.ai.backend.dto.response.PlanResponse;
import org.springframework.data.domain.Page;

public interface PlanService {
    PlanResponse createPlan(PlanRequest request);
    PlanResponse getPlanById(Long id);
    Page<PlanResponse> getAllPlans(int page, int size, boolean activeOnly);
    PlanResponse updatePlan(Long id, PlanRequest request);
    void changePlanStatus(Long id, boolean isActive);
}
