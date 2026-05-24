package com.willa.ai.backend.config;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.willa.ai.backend.entity.Plan;
import com.willa.ai.backend.entity.Subscription;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.Wallet;
import com.willa.ai.backend.entity.enums.BillingCycle;
import com.willa.ai.backend.entity.enums.SubscriptionStatus;
import com.willa.ai.backend.repository.PlanRepository;
import com.willa.ai.backend.repository.SubscriptionRepository;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.repository.WalletRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private static final String PRO_PLAN_NAME = "Pro";
    private static final String DEV_PRO_EMAIL = "phnanhky@gmail.com";
    private static final long DEV_PRO_TOKEN_BALANCE = 1_000_000_000L;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final WalletRepository walletRepository;

    @Value("${admin.default.email}")
    private String adminEmail;

    @Value("${admin.default.password}")
    private String adminPassword;

    @Value("${admin.default.name}")
    private String adminName;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        cleanupSubscriptions();
        updateUsersStatus();
        ensureProPlanExists();
        upgradeUserToPro(DEV_PRO_EMAIL, DEV_PRO_TOKEN_BALANCE);
    }

    /** Tạo gói Pro nếu DB mới (Docker volume trống) — tránh crash startup. */
    private Plan ensureProPlanExists() {
        Optional<Plan> existing = planRepository.findAll().stream()
                .filter(p -> PRO_PLAN_NAME.equalsIgnoreCase(p.getName()))
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        Plan proPlan = Plan.builder()
                .name(PRO_PLAN_NAME)
                .description("Pro plan (auto-seeded)")
                .price(BigDecimal.ZERO)
                .billingCycle(BillingCycle.MONTHLY)
                .tokenLimit(1_000_000)
                .isActive(true)
                .build();
        Plan saved = planRepository.save(proPlan);
        log.info("DataInit: created missing active plan '{}'", PRO_PLAN_NAME);
        return saved;
    }

    private void upgradeUserToPro(String email, long tokenBalance) {
        String normalizedEmail = email.trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);
        if (userOpt.isEmpty()) {
            log.warn("DataInit: user {} not found — register first, then restart backend.", normalizedEmail);
            return;
        }

        User user = userOpt.get();
        Plan proPlan = ensureProPlanExists();

        subscriptionRepository.findByUserIdAndStatus(user.getId(), SubscriptionStatus.ACTIVE)
                .forEach(sub -> {
                    sub.setStatus(SubscriptionStatus.CANCELLED);
                    subscriptionRepository.save(sub);
                });

        LocalDateTime now = LocalDateTime.now();
        Subscription subscription = Subscription.builder()
                .user(user)
                .plan(proPlan)
                .startDate(now)
                .endDate(now.plusYears(100))
                .status(SubscriptionStatus.ACTIVE)
                .build();
        subscriptionRepository.save(subscription);

        Wallet wallet = walletRepository.findByUserId(user.getId())
                .orElse(Wallet.builder()
                        .user(user)
                        .tokenBalance(0L)
                        .totalRecharged(0L)
                        .build());
        wallet.setTokenBalance(tokenBalance);
        wallet.setTotalRecharged(tokenBalance);
        walletRepository.save(wallet);

        user.setRequiresReview(false);
        user.setIsEnabled(true);
        user.setIsActive(true);
        userRepository.save(user);

        log.info("DataInit: {} upgraded to PRO with tokenBalance={}", normalizedEmail, tokenBalance);
    }

    private void updateUsersStatus() {
        log.info("Updating all users to isEnabled=true and isActive=true...");
        List<User> users = userRepository.findAll();
        for (User user : users) {
            boolean changed = false;
            if (user.getIsEnabled() == null || !user.getIsEnabled()) {
                user.setIsEnabled(true);
                changed = true;
            }
            if (user.getIsActive() == null || !user.getIsActive()) {
                user.setIsActive(true);
                changed = true;
            }
            if (changed) {
                userRepository.save(user);
                log.info("Updated user {} (ID: {}) status to enabled and active.", user.getEmail(), user.getId());
            }
        }
    }

    private void cleanupSubscriptions() {
        log.info("Cleaning up subscriptions...");
        List<Long> idsToDelete = List.of(419L);
        for (Long id : idsToDelete) {
            subscriptionRepository.findById(id).ifPresent(sub -> {
                subscriptionRepository.delete(sub);
                log.info("Deleted subscription ID: {}", id);
            });
        }
    }
}
