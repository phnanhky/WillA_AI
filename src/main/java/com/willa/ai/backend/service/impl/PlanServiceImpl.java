package com.willa.ai.backend.service.impl;

import com.willa.ai.backend.dto.request.PlanRequest;
import com.willa.ai.backend.dto.response.PlanResponse;
import com.willa.ai.backend.entity.Plan;
import com.willa.ai.backend.exception.ResourceNotFoundException;
import com.willa.ai.backend.repository.PlanRepository;
import com.willa.ai.backend.service.PlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlanServiceImpl implements PlanService {

    private final PlanRepository planRepository;

    @Override
    @Transactional
    public PlanResponse createPlan(PlanRequest request) {
        Plan plan = Plan.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .billingCycle(request.getBillingCycle())
                .tokenLimit(request.getTokenLimit())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        Plan savedPlan = planRepository.save(plan);
        return mapToResponse(savedPlan);
    }

    @Override
    public PlanResponse getPlanById(Long id) {
        Plan plan = planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found with id: " + id));
        return mapToResponse(plan);
    }

    @Override
    public Page<PlanResponse> getAllPlans(int page, int size, boolean activeOnly) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Plan> plans;
        
        if (activeOnly) {
            plans = planRepository.findByIsActiveTrue(pageable);
        } else {
            plans = planRepository.findAll(pageable);
        }
        
        return plans.map(this::mapToResponse);
    }

    @Override
    @Transactional
    public PlanResponse updatePlan(Long id, PlanRequest request) {
        Plan plan = planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found with id: " + id));

        plan.setName(request.getName());
        plan.setDescription(request.getDescription());
        plan.setPrice(request.getPrice());
        plan.setBillingCycle(request.getBillingCycle());
        plan.setTokenLimit(request.getTokenLimit());
        
        if (request.getIsActive() != null) {
            plan.setIsActive(request.getIsActive());
        }

        Plan updatedPlan = planRepository.save(plan);
        return mapToResponse(updatedPlan);
    }

    @Override
    @Transactional
    public void changePlanStatus(Long id, boolean isActive) {
        Plan plan = planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found with id: " + id));
        
        plan.setIsActive(isActive);
        planRepository.save(plan);
    }

    private PlanResponse mapToResponse(Plan plan) {
        return PlanResponse.builder()
                .id(plan.getId())
                .name(plan.getName())
                .description(plan.getDescription())
                .price(plan.getPrice())
                .billingCycle(plan.getBillingCycle())
                .tokenLimit(plan.getTokenLimit())
                .isActive(plan.getIsActive())
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .build();
    }
}
