package com.willa.ai.backend.cron;

import com.willa.ai.backend.entity.WorkspacePlan;
import com.willa.ai.backend.entity.WorkspaceSubscription;
import com.willa.ai.backend.entity.enums.BillingCycle;
import com.willa.ai.backend.entity.enums.SubscriptionStatus;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.repository.WorkspaceSubscriptionRepository;
import com.willa.ai.backend.service.WorkspacePlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkspaceSubscriptionCronTask {

    private final WorkspaceSubscriptionRepository workspaceSubscriptionRepository;
    private final WorkspacePlanService workspacePlanService;
    private final UserRepository userRepository;

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void processExpiredWorkspaceSubscriptions() {
        log.info("Running cron job: processExpiredWorkspaceSubscriptions");
        LocalDateTime now = LocalDateTime.now();
        List<WorkspaceSubscription> activeSubscriptions =
                workspaceSubscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE);

        for (WorkspaceSubscription sub : activeSubscriptions) {
            if (sub.getWorkspacePlan().getBillingCycle() == BillingCycle.ONE_TIME) {
                continue;
            }
            if (sub.getEndDate() == null || !sub.getEndDate().isBefore(now)) {
                continue;
            }

            log.info("Workspace subscription id {} expired. User: {}", sub.getId(), sub.getUser().getEmail());
            sub.setStatus(SubscriptionStatus.EXPIRED);
            workspaceSubscriptionRepository.save(sub);

            boolean hasOtherActiveRecurring = workspaceSubscriptionRepository
                    .findActiveRecurringByUserId(sub.getUser().getId(), SubscriptionStatus.ACTIVE)
                    .stream()
                    .anyMatch(other -> !other.getId().equals(sub.getId()));
            if (hasOtherActiveRecurring) {
                continue;
            }

            WorkspacePlan freePlan = workspacePlanService.getDefaultPlan();
            WorkspaceSubscription newFreeSub = WorkspaceSubscription.builder()
                    .user(sub.getUser())
                    .workspacePlan(freePlan)
                    .startDate(now)
                    .endDate(now.plusYears(100))
                    .status(SubscriptionStatus.ACTIVE)
                    .build();
            workspaceSubscriptionRepository.save(newFreeSub);

            sub.getUser().setWorkspacePlan(freePlan);
            try {
                sub.getUser().setWorkspacePlanTier(
                        com.willa.ai.backend.entity.enums.WorkspacePlanTier.valueOf(freePlan.getCode()));
            } catch (IllegalArgumentException ignored) {
                sub.getUser().setWorkspacePlanTier(
                        com.willa.ai.backend.entity.enums.WorkspacePlanTier.FREE_WORKSPACE);
            }
            userRepository.save(sub.getUser());
            log.info("Assigned permanent Free workspace plan to user: {}", sub.getUser().getEmail());
        }
    }
}
