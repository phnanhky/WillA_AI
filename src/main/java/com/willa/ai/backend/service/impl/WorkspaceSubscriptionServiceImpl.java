package com.willa.ai.backend.service.impl;

import com.willa.ai.backend.dto.response.WorkspaceSubscriptionResponse;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.WorkspacePlan;
import com.willa.ai.backend.entity.WorkspaceSubscription;
import com.willa.ai.backend.entity.enums.BillingCycle;
import com.willa.ai.backend.entity.enums.SubscriptionStatus;
import com.willa.ai.backend.entity.enums.WorkspacePlanTier;
import com.willa.ai.backend.exception.ResourceNotFoundException;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.repository.WorkspacePlanRepository;
import com.willa.ai.backend.repository.WorkspaceSubscriptionRepository;
import com.willa.ai.backend.service.WorkspacePlanService;
import com.willa.ai.backend.service.WorkspaceSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkspaceSubscriptionServiceImpl implements WorkspaceSubscriptionService {

    private final WorkspaceSubscriptionRepository workspaceSubscriptionRepository;
    private final WorkspacePlanRepository workspacePlanRepository;
    private final UserRepository userRepository;
    private final WorkspacePlanService workspacePlanService;

    @Override
    @Transactional
    public Page<WorkspaceSubscriptionResponse> getUserSubscriptions(String email, int page, int size) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        Pageable pageable = PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by("createdAt").descending());
        Page<WorkspaceSubscription> subscriptions = workspaceSubscriptionRepository.findByUserId(user.getId(), pageable);
        LocalDateTime now = LocalDateTime.now();
        subscriptions.forEach(sub -> checkAndExpireSubscriptionInDb(sub, now));
        return subscriptions.map(this::mapToResponse);
    }

    @Override
    @Transactional
    public Page<WorkspaceSubscriptionResponse> getAllSubscriptions(int page, int size) {
        Pageable pageable = PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by("createdAt").descending());
        Page<WorkspaceSubscription> subscriptions = workspaceSubscriptionRepository.findAll(pageable);
        LocalDateTime now = LocalDateTime.now();
        subscriptions.forEach(sub -> checkAndExpireSubscriptionInDb(sub, now));
        return subscriptions.map(this::mapToResponse);
    }

    @Override
    @Transactional
    public WorkspaceSubscriptionResponse cancelSubscription(String email, Long subscriptionId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        WorkspaceSubscription subscription = workspaceSubscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace subscription not found"));

        if (!subscription.getUser().getId().equals(user.getId()) && !user.getRole().name().equals("ADMIN")) {
            throw new IllegalArgumentException("Not authorized to cancel this subscription");
        }

        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new IllegalArgumentException("Only active subscriptions can be cancelled");
        }

        subscription.setStatus(SubscriptionStatus.CANCELLED);
        WorkspaceSubscription updated = workspaceSubscriptionRepository.save(subscription);
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public void createOrUpdateSubscription(String email, Long workspacePlanId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        WorkspacePlan plan = workspacePlanRepository.findById(workspacePlanId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace plan not found with id: " + workspacePlanId));

        if (!Boolean.TRUE.equals(plan.getIsActive())) {
            throw new IllegalArgumentException("Cannot subscribe to an inactive workspace plan");
        }

        validateStudentPlan(user, plan);

        cancelActiveRecurring(user);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = calculateEndDate(now, plan);

        WorkspaceSubscription subscription = WorkspaceSubscription.builder()
                .user(user)
                .workspacePlan(plan)
                .startDate(now)
                .endDate(endDate)
                .status(SubscriptionStatus.ACTIVE)
                .build();
        workspaceSubscriptionRepository.save(subscription);

        syncUserWorkspacePlan(user, plan);
        log.info("Activated workspace subscription for user {} plan {}", email, plan.getCode());
    }

    @Override
    @Transactional
    public void assignDefaultFreeSubscription(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        WorkspacePlan freePlan = workspacePlanService.getDefaultPlan();
        LocalDateTime now = LocalDateTime.now();

        WorkspaceSubscription subscription = WorkspaceSubscription.builder()
                .user(user)
                .workspacePlan(freePlan)
                .startDate(now)
                .endDate(now.plusYears(100))
                .status(SubscriptionStatus.ACTIVE)
                .build();
        workspaceSubscriptionRepository.save(subscription);
        syncUserWorkspacePlan(user, freePlan);
    }

    private void cancelActiveRecurring(User user) {
        List<WorkspaceSubscription> activeSubs = workspaceSubscriptionRepository.findActiveRecurringByUserId(
                user.getId(), SubscriptionStatus.ACTIVE);
        for (WorkspaceSubscription sub : activeSubs) {
            sub.setStatus(SubscriptionStatus.CANCELLED);
            workspaceSubscriptionRepository.save(sub);
            log.info("Cancelled workspace subscription {} for user {}", sub.getId(), user.getEmail());
        }
    }

    private LocalDateTime calculateEndDate(LocalDateTime startDate, WorkspacePlan plan) {
        if (isFreePlan(plan)) {
            return startDate.plusYears(100);
        }
        return switch (plan.getBillingCycle()) {
            case MONTHLY -> startDate.plusMonths(1);
            case YEARLY -> startDate.plusYears(1);
            case ONE_TIME -> startDate.plusYears(100);
        };
    }

    private boolean isFreePlan(WorkspacePlan plan) {
        return Boolean.TRUE.equals(plan.getIsDefault())
                || plan.getCode().toUpperCase().contains("FREE")
                || (plan.getPrice() != null && plan.getPrice().signum() == 0
                && (plan.getPromotionalPrice() == null || plan.getPromotionalPrice().signum() == 0));
    }

    private void validateStudentPlan(User user, WorkspacePlan plan) {
        String code = plan.getCode() != null ? plan.getCode().toLowerCase() : "";
        String name = plan.getName() != null ? plan.getName().toLowerCase() : "";
        if ((code.contains("student") || name.contains("student"))
                && !Boolean.TRUE.equals(user.getIsStudent())) {
            throw new IllegalArgumentException("Chỉ tài khoản đã xác thực sinh viên mới được đăng ký gói Student.");
        }
    }

    private void syncUserWorkspacePlan(User user, WorkspacePlan plan) {
        user.setWorkspacePlan(plan);
        try {
            user.setWorkspacePlanTier(WorkspacePlanTier.valueOf(plan.getCode()));
        } catch (IllegalArgumentException ignored) {
            if (user.getWorkspacePlanTier() == null) {
                user.setWorkspacePlanTier(WorkspacePlanTier.FREE_WORKSPACE);
            }
        }
        userRepository.save(user);
    }

    private void assignFreePlanIfNoActiveRecurring(User user, LocalDateTime now) {
        boolean hasOtherActiveRecurring = workspaceSubscriptionRepository
                .findActiveRecurringByUserId(user.getId(), SubscriptionStatus.ACTIVE)
                .stream()
                .anyMatch(sub -> sub.getStatus() == SubscriptionStatus.ACTIVE);
        if (!hasOtherActiveRecurring) {
            WorkspacePlan freePlan = workspacePlanService.getDefaultPlan();
            WorkspaceSubscription newFreeSub = WorkspaceSubscription.builder()
                    .user(user)
                    .workspacePlan(freePlan)
                    .startDate(now)
                    .endDate(now.plusYears(100))
                    .status(SubscriptionStatus.ACTIVE)
                    .build();
            workspaceSubscriptionRepository.save(newFreeSub);
            syncUserWorkspacePlan(user, freePlan);
            log.info("Assigned permanent Free workspace plan to user: {}", user.getEmail());
        }
    }

    private void checkAndExpireSubscriptionInDb(WorkspaceSubscription sub, LocalDateTime now) {
        if (sub.getStatus() != SubscriptionStatus.ACTIVE) {
            return;
        }
        if (sub.getWorkspacePlan().getBillingCycle() == BillingCycle.ONE_TIME) {
            return;
        }
        if (sub.getEndDate() == null || !sub.getEndDate().isBefore(now)) {
            return;
        }

        sub.setStatus(SubscriptionStatus.EXPIRED);
        workspaceSubscriptionRepository.save(sub);

        boolean hasOtherActiveRecurring = workspaceSubscriptionRepository
                .findActiveRecurringByUserId(sub.getUser().getId(), SubscriptionStatus.ACTIVE)
                .stream()
                .anyMatch(other -> !other.getId().equals(sub.getId())
                        && other.getStatus() == SubscriptionStatus.ACTIVE);
        if (!hasOtherActiveRecurring) {
            assignFreePlanIfNoActiveRecurring(sub.getUser(), now);
        }
    }

    private WorkspaceSubscriptionResponse mapToResponse(WorkspaceSubscription subscription) {
        SubscriptionStatus currentStatus = subscription.getStatus();
        if (currentStatus == SubscriptionStatus.ACTIVE
                && subscription.getEndDate() != null
                && subscription.getEndDate().isBefore(LocalDateTime.now())) {
            currentStatus = SubscriptionStatus.EXPIRED;
        }
        WorkspacePlan plan = subscription.getWorkspacePlan();
        return WorkspaceSubscriptionResponse.builder()
                .id(subscription.getId())
                .userId(subscription.getUser().getId())
                .userEmail(subscription.getUser().getEmail())
                .workspacePlanId(plan.getId())
                .workspacePlanCode(plan.getCode())
                .workspacePlanName(plan.getName())
                .billingCycle(plan.getBillingCycle())
                .startDate(subscription.getStartDate())
                .endDate(subscription.getEndDate())
                .status(currentStatus)
                .createdAt(subscription.getCreatedAt())
                .build();
    }
}
