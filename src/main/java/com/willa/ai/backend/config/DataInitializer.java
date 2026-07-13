package com.willa.ai.backend.config;

import com.willa.ai.backend.entity.Plan;
import com.willa.ai.backend.entity.Subscription;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.enums.BillingCycle;
import com.willa.ai.backend.entity.enums.SubscriptionStatus;
import com.willa.ai.backend.repository.PlanRepository;
import com.willa.ai.backend.repository.SubscriptionRepository;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-off: nâng {@link #TARGET_EMAIL} lên Pro Feedback MONTHLY.
 * Hủy gói recurring đang active (Free/Student/Pro cũ) rồi kích hoạt Pro + cộng token.
 * Chạy xong trên VPS → xóa {@code @Component} hoặc cả class này.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private static final String TARGET_EMAIL = "nvnchau2004@gmail.com";

    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;

    @Override
    @Transactional
    public void run(String... args) {
        User user = userRepository.findByEmail(TARGET_EMAIL).orElse(null);
        if (user == null) {
            log.warn("[DataInit] Bỏ qua — chưa có user {}", TARGET_EMAIL);
            return;
        }

        Plan proMonthly = planRepository.findAll().stream()
                .filter(Plan::getIsActive)
                .filter(p -> "Pro".equalsIgnoreCase(p.getName()))
                .filter(p -> p.getBillingCycle() == BillingCycle.MONTHLY)
                .findFirst()
                .orElse(null);
        if (proMonthly == null) {
            log.error("[DataInit] Không tìm thấy gói Pro MONTHLY active — bỏ qua.");
            return;
        }

        boolean alreadyProMonthly = subscriptionRepository
                .findActiveRecurringByUserId(user.getId(), SubscriptionStatus.ACTIVE)
                .stream()
                .anyMatch(sub -> isProMonthly(sub, proMonthly.getId()));
        if (alreadyProMonthly) {
            log.info("[DataInit] {} đã có Pro Feedback MONTHLY active — bỏ qua.", TARGET_EMAIL);
            return;
        }

        log.info("[DataInit] Nâng {} lên Pro Feedback MONTHLY (planId={})…", TARGET_EMAIL, proMonthly.getId());
        subscriptionService.createOrUpdateSubscription(user.getEmail(), proMonthly.getId());
        log.info(
                "[DataInit] Xong: userId={}, plan=Pro MONTHLY, tokenGrant={}. Xóa @Component sau khi verify.",
                user.getId(),
                proMonthly.getTokenLimit());
    }

    private static boolean isProMonthly(Subscription sub, long proMonthlyPlanId) {
        Plan plan = sub.getPlan();
        return plan != null
                && plan.getId().equals(proMonthlyPlanId)
                && "Pro".equalsIgnoreCase(plan.getName())
                && plan.getBillingCycle() == BillingCycle.MONTHLY;
    }
}
