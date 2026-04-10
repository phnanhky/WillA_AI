package com.willa.ai.backend.cron;

import com.willa.ai.backend.entity.Plan;
import com.willa.ai.backend.entity.Subscription;
import com.willa.ai.backend.entity.Wallet;
import com.willa.ai.backend.entity.enums.SubscriptionStatus;
import com.willa.ai.backend.repository.PlanRepository;
import com.willa.ai.backend.repository.SubscriptionRepository;
import com.willa.ai.backend.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionCronTask {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;

    /**
     * Run every day at midnight to check expired subscriptions.
     * If an active subscription has passed its end date, update its status to EXPIRED.
     * Then assign the user back to the Free plan.
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void processExpiredSubscriptions() {
        log.info("Running cron job: processExpiredSubscriptions");
        LocalDateTime now = LocalDateTime.now();
        List<Subscription> activeSubscriptions = subscriptionRepository.findSubscriptionsByStatus(SubscriptionStatus.ACTIVE);
        
        for (Subscription sub : activeSubscriptions) {
            if (sub.getEndDate() != null && sub.getEndDate().isBefore(now)) {
                log.info("Subscription id {} has expired. User: {}", sub.getId(), sub.getUser().getEmail());
                sub.setStatus(SubscriptionStatus.EXPIRED);
                subscriptionRepository.save(sub);

                // Revert user to "Free" plan automatically
                planRepository.findByName("Free").ifPresent(freePlan -> {
                    Subscription newFreeSub = Subscription.builder()
                            .user(sub.getUser())
                            .plan(freePlan)
                            .startDate(now)
                            .endDate(now.plusYears(100)) // Gói Free không bao giờ hết hạn
                            .status(SubscriptionStatus.ACTIVE)
                            .build();
                    subscriptionRepository.save(newFreeSub);
                    log.info("Assigned permanent Free plan to user: {}", sub.getUser().getEmail());
                });
            }
        }
    }
}
