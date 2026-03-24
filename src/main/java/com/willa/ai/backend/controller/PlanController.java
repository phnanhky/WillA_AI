package com.willa.ai.backend.controller;

import com.willa.ai.backend.dto.request.PlanRequest;
import com.willa.ai.backend.dto.response.ApiResponse;
import com.willa.ai.backend.dto.response.PlanResponse;
import com.willa.ai.backend.service.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
@Tag(name = "Plan", description = "AI Subscription Plan management APIs")
public class PlanController {

    private final PlanService planService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> createPlan(@Valid @RequestBody PlanRequest request) {
        PlanResponse createdPlan = planService.createPlan(request);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("Plan created successfully")
                .data(createdPlan)
                .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse> getPlanById(@PathVariable Long id) {
        PlanResponse plan = planService.getPlanById(id);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("Plan retrieved successfully")
                .data(plan)
                .build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse> getAllPlans(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        Page<PlanResponse> plans = planService.getAllPlans(page, size, activeOnly);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("Plans retrieved successfully")
                .data(plans)
                .build());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> updatePlan(
            @PathVariable Long id,
            @Valid @RequestBody PlanRequest request) {
        PlanResponse updatedPlan = planService.updatePlan(id, request);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("Plan updated successfully")
                .data(updatedPlan)
                .build());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> changePlanStatus(
            @PathVariable Long id,
            @RequestParam boolean isActive) {
        planService.changePlanStatus(id, isActive);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("Plan status updated successfully")
                .build());
    }
}
