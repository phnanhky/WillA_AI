package com.willa.ai.backend.config;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.boot.CommandLineRunner;
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

/**
 * Seed tối thiểu khi startup — không xóa subscription/user, không reset toàn DB.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private static final String PRO_PLAN_NAME = "Pro";
    private static final String DEV_EMAIL = "phnanhky@gmail.com";
    private static final long DEV_WALLET_TOKENS = 200_000L;

    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final WalletRepository walletRepository;

    @Override
    @Transactional
    public void run(String... args) {
        ensureProPlanExists();
        topUpDevAccount(DEV_EMAIL, DEV_WALLET_TOKENS);
    }

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

    /**
     * Nạp token + đảm bảo có gói Pro active (chỉ tạo mới nếu chưa có sub ACTIVE).
     * Không hủy subscription hiện có, không sửa user khác.
     */
    private void topUpDevAccount(String email, long tokenBalance) {
        String normalized = email.trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmail(normalized);
        if (userOpt.isEmpty()) {
            log.warn("DataInit: user {} not found — register first, then restart backend.", normalized);
            return;
        }

        User user = userOpt.get();
        Plan proPlan = ensureProPlanExists();

        boolean hasActiveSub = subscriptionRepository
                .findByUserIdAndStatus(user.getId(), SubscriptionStatus.ACTIVE)
                .stream()
                .anyMatch(s -> s.getPlan() != null
                        && PRO_PLAN_NAME.equalsIgnoreCase(s.getPlan().getName()));

        if (!hasActiveSub) {
            LocalDateTime now = LocalDateTime.now();
            subscriptionRepository.save(Subscription.builder()
                    .user(user)
                    .plan(proPlan)
                    .startDate(now)
                    .endDate(now.plusYears(100))
                    .status(SubscriptionStatus.ACTIVE)
                    .build());
            log.info("DataInit: created ACTIVE Pro subscription for {}", normalized);
        }

        Wallet wallet = walletRepository.findByUserId(user.getId())
                .orElse(Wallet.builder()
                        .user(user)
                        .tokenBalance(0L)
                        .totalRecharged(0L)
                        .build());

        wallet.setTokenBalance(tokenBalance);
        if (wallet.getTotalRecharged() == null || wallet.getTotalRecharged() < tokenBalance) {
            wallet.setTotalRecharged(tokenBalance);
        }
        walletRepository.save(wallet);

        if (Boolean.TRUE.equals(user.getRequiresReview())) {
            user.setRequiresReview(false);
            userRepository.save(user);
        }

        log.info("DataInit: {} wallet tokenBalance={}", normalized, tokenBalance);
    }
}
