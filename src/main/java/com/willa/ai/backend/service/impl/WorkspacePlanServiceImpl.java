package com.willa.ai.backend.service.impl;

import com.willa.ai.backend.dto.request.WorkspacePlanRequest;
import com.willa.ai.backend.dto.response.WorkspacePlanResponse;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.WorkspacePlan;
import com.willa.ai.backend.entity.enums.WorkspacePlanTier;
import com.willa.ai.backend.repository.WorkspacePlanRepository;
import com.willa.ai.backend.service.WorkspacePlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkspacePlanServiceImpl implements WorkspacePlanService {

    private final WorkspacePlanRepository workspacePlanRepository;

    @Override
    @Transactional(readOnly = true)
    public List<WorkspacePlanResponse> listAll() {
        return workspacePlanRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspacePlanResponse> listActive() {
        return workspacePlanRepository.findByIsActiveTrueOrderBySortOrderAscNameAsc().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspacePlanResponse getById(Long id) {
        return mapToResponse(getEntity(id));
    }

    @Override
    public WorkspacePlanResponse create(WorkspacePlanRequest request) {
        String code = normalizeCode(request.getCode());
        if (workspacePlanRepository.existsByCode(code)) {
            throw new RuntimeException("Mã gói workspace đã tồn tại: " + code);
        }
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            clearDefaultFlag();
        }
        WorkspacePlan saved = workspacePlanRepository.save(buildFromRequest(new WorkspacePlan(), request, code));
        ensureDefaultExists();
        return mapToResponse(saved);
    }

    @Override
    public WorkspacePlanResponse update(Long id, WorkspacePlanRequest request) {
        WorkspacePlan plan = getEntity(id);
        String code = normalizeCode(request.getCode());
        if (!plan.getCode().equals(code) && workspacePlanRepository.existsByCode(code)) {
            throw new RuntimeException("Mã gói workspace đã tồn tại: " + code);
        }
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            clearDefaultExcept(id);
        }
        WorkspacePlan saved = workspacePlanRepository.save(buildFromRequest(plan, request, code));
        ensureDefaultExists();
        return mapToResponse(saved);
    }

    @Override
    public void changeStatus(Long id, boolean isActive) {
        WorkspacePlan plan = getEntity(id);
        if (!isActive && Boolean.TRUE.equals(plan.getIsDefault())) {
            throw new RuntimeException("Không thể tắt gói workspace mặc định");
        }
        plan.setIsActive(isActive);
        workspacePlanRepository.save(plan);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspacePlan resolveForUser(User user) {
        if (user == null) {
            return getDefaultPlan();
        }
        if (user.getWorkspacePlan() != null) {
            return user.getWorkspacePlan();
        }
        if (user.getWorkspacePlanTier() != null) {
            return workspacePlanRepository.findByCode(user.getWorkspacePlanTier().name())
                    .orElseGet(this::getDefaultPlan);
        }
        return getDefaultPlan();
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspacePlan getDefaultPlan() {
        return workspacePlanRepository.findByIsDefaultTrue()
                .or(() -> workspacePlanRepository.findByCode(WorkspacePlanTier.FREE_WORKSPACE.name()))
                .orElseThrow(() -> new RuntimeException("Chưa cấu hình gói workspace mặc định"));
    }

    @Override
    @Transactional(readOnly = true)
    public int maxOwnedWorkspaces(User user) {
        Integer max = resolveForUser(user).getMaxOwnedWorkspaces();
        return max == null ? Integer.MAX_VALUE : max;
    }

    @Override
    @Transactional(readOnly = true)
    public int maxMembersPerWorkspace(User user) {
        Integer max = resolveForUser(user).getMaxMembersPerWorkspace();
        return max == null ? Integer.MAX_VALUE : max;
    }

    @Override
    @Transactional(readOnly = true)
    public String displayNameForUser(User user) {
        return resolveForUser(user).getName();
    }

    private WorkspacePlan getEntity(Long id) {
        return workspacePlanRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy gói workspace"));
    }

    private void clearDefaultFlag() {
        workspacePlanRepository.findAll().forEach(p -> {
            if (Boolean.TRUE.equals(p.getIsDefault())) {
                p.setIsDefault(false);
                workspacePlanRepository.save(p);
            }
        });
    }

    private void clearDefaultExcept(Long id) {
        workspacePlanRepository.findAll().forEach(p -> {
            if (!p.getId().equals(id) && Boolean.TRUE.equals(p.getIsDefault())) {
                p.setIsDefault(false);
                workspacePlanRepository.save(p);
            }
        });
    }

    private void ensureDefaultExists() {
        if (workspacePlanRepository.findByIsDefaultTrue().isEmpty()) {
            workspacePlanRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                    .findFirst()
                    .ifPresent(p -> {
                        p.setIsDefault(true);
                        workspacePlanRepository.save(p);
                    });
        }
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase().replace(" ", "_");
    }

    private WorkspacePlan buildFromRequest(WorkspacePlan plan, WorkspacePlanRequest request, String code) {
        plan.setCode(code);
        plan.setName(request.getName().trim());
        plan.setDescription(trimOrNull(request.getDescription()));
        plan.setPrice(request.getPrice());
        plan.setBillingCycle(request.getBillingCycle());
        plan.setDiscountPercentage(request.getDiscountPercentage());
        plan.setPromotionalPrice(calculatePromo(request.getPrice(), request.getDiscountPercentage()));
        plan.setMaxOwnedWorkspaces(request.getMaxOwnedWorkspaces());
        plan.setMaxMembersPerWorkspace(request.getMaxMembersPerWorkspace());
        if (request.getIsActive() != null) {
            plan.setIsActive(request.getIsActive());
        } else if (plan.getIsActive() == null) {
            plan.setIsActive(true);
        }
        if (request.getIsDefault() != null) {
            plan.setIsDefault(request.getIsDefault());
        } else if (plan.getIsDefault() == null) {
            plan.setIsDefault(false);
        }
        if (request.getSortOrder() != null) {
            plan.setSortOrder(request.getSortOrder());
        } else if (plan.getSortOrder() == null) {
            plan.setSortOrder(0);
        }
        return plan;
    }

    private BigDecimal calculatePromo(BigDecimal basePrice, Double discountPercentage) {
        BigDecimal promo = basePrice;
        if (discountPercentage != null && discountPercentage > 0) {
            promo = basePrice.subtract(basePrice.multiply(BigDecimal.valueOf(discountPercentage / 100.0)));
        }
        return promo.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : promo;
    }

    private String trimOrNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private WorkspacePlanResponse mapToResponse(WorkspacePlan plan) {
        return WorkspacePlanResponse.builder()
                .id(plan.getId())
                .code(plan.getCode())
                .name(plan.getName())
                .description(plan.getDescription())
                .price(plan.getPrice())
                .billingCycle(plan.getBillingCycle())
                .discountPercentage(plan.getDiscountPercentage())
                .promotionalPrice(plan.getPromotionalPrice())
                .maxOwnedWorkspaces(plan.getMaxOwnedWorkspaces())
                .maxMembersPerWorkspace(plan.getMaxMembersPerWorkspace())
                .isActive(plan.getIsActive())
                .isDefault(plan.getIsDefault())
                .sortOrder(plan.getSortOrder())
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .build();
    }
}
