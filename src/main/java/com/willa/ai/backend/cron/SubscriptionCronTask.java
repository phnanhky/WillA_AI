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
    private final WalletRepository walletRepository;
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
        List<Subscription> activeSubscriptions = subscriptionRepository.findAll();
        
        for (Subscription sub : activeSubscriptions) {
            if ((sub.getStatus() == SubscriptionStatus.ACTIVE || sub.getStatus() == SubscriptionStatus.CANCELLED) 
                    && sub.getEndDate().isBefore(now)) {
                log.info("Subscription id {} has expired. User: {}", sub.getId(), sub.getUser().getEmail());
                sub.setStatus(SubscriptionStatus.EXPIRED);
                subscriptionRepository.save(sub);

                // Update user to free plan if they don't have any other active plans
                assignFreePlan(sub.getUser().getId());
            }
        }
    }

    /**
     * Run at 00:00 on the 1st day of each month.
     * Reset token balances for users who ONLY have the Free plan.
     */
    @Scheduled(cron = "0 0 0 1 * *")
    public void resetFreePlanTokens() {
        log.info("Running cron job: resetFreePlanTokens");
        Plan freePlan = planRepository.findByName("Free").orElse(null);
        if (freePlan == null) return;
        
        List<Subscription> allSubscriptions = subscriptionRepository.findAll();
        for (Subscription sub : allSubscriptions) {
            if (sub.getStatus() == SubscriptionStatus.ACTIVE && sub.getPlan().getId().equals(freePlan.getId())) {
                walletRepository.findByUserId(sub.getUser().getId()).ifPresent(wallet -> {
                    // Update tokens to 60000 limit
                    wallet.setTokenBalance((long) freePlan.getTokenLimit());
                    walletRepository.save(wallet);
                    log.info("Reset tokens for Free plan user: {}", sub.getUser().getEmail());
                });
            }
        }
    }

    private void assignFreePlan(Long userId) {
        Plan freePlan = planRepository.findByName("Free").orElse(null);
        if (freePlan != null) {
            List<Subscription> activeSubs = subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
            if (activeSubs.isEmpty()) {
                Subscription freeSub = Subscription.builder()
                        .user(com.willa.ai.backend.entity.User.builder().id(userId).build())
                        .plan(freePlan)
                        .startDate(LocalDateTime.now())
                        .endDate(LocalDateTime.now().plusMonths(1))
                        .status(SubscriptionStatus.ACTIVE)
                        .build();
                subscriptionRepository.save(freeSub);

                walletRepository.findByUserId(userId).ifPresent(wallet -> {
                    wallet.setTokenBalance((long) freePlan.getTokenLimit());
                    walletRepository.save(wallet);
                });
            }
        }
    }
}
