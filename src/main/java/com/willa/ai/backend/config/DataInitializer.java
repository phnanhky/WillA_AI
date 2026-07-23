package com.willa.ai.backend.config;

import com.willa.ai.backend.entity.Plan;
import com.willa.ai.backend.entity.Subscription;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.Wallet;
import com.willa.ai.backend.entity.WorkspacePlan;
import com.willa.ai.backend.entity.WorkspaceSubscription;
import com.willa.ai.backend.entity.enums.Role;
import com.willa.ai.backend.entity.enums.SubscriptionStatus;
import com.willa.ai.backend.entity.enums.WorkspacePlanTier;
import com.willa.ai.backend.repository.PlanRepository;
import com.willa.ai.backend.repository.SubscriptionRepository;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.repository.WalletRepository;
import com.willa.ai.backend.repository.WorkspaceSubscriptionRepository;
import com.willa.ai.backend.service.WorkspacePlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * One-off: tạo / kích hoạt 10 account demo (enabled + active), password chung.
 * Chạy xong trên VPS → xóa {@code @Component} hoặc làm rỗng {@link #run}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private static final String PLAIN_PASSWORD = "123456789";

    private static final List<String> EMAILS = List.of(
            "anhnaaa739@gmail.com",
            "hunghuyen433@gmail.com",
            "chanvn73@gmail.com",
            "nan397550@gmail.com",
            "cn4170384@gmail.com",
            "quyenhan919@gmail.com",
            "thuyhyqq@gmail.com",
            "quanquannguyenne@gmail.com",
            "ngocanhtran69@gmail.com",
            "taolamynuday@gmail.com"
    );

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final WorkspacePlanService workspacePlanService;
    private final WorkspaceSubscriptionRepository workspaceSubscriptionRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        String encoded = passwordEncoder.encode(PLAIN_PASSWORD);
        int created = 0;
        int updated = 0;

        for (String email : EMAILS) {
            String normalized = email.trim().toLowerCase(Locale.ROOT);
            Optional<User> existing = userRepository.findByEmail(normalized);
            if (existing.isPresent()) {
                User user = existing.get();
                user.setPassword(encoded);
                user.setIsEnabled(true);
                user.setIsActive(true);
                user.setVerificationToken(null);
                userRepository.save(user);
                ensureEntitlements(user);
                updated++;
                log.info("Activated existing account: {}", normalized);
            } else {
                User user = User.builder()
                        .email(normalized)
                        .fullName(displayNameFromEmail(normalized))
                        .password(encoded)
                        .role(Role.USER)
                        .isEnabled(true)
                        .isActive(true)
                        .isStudent(false)
                        .requiresReview(false)
                        .workspacePlanTier(WorkspacePlanTier.FREE_WORKSPACE)
                        .build();
                user = userRepository.save(user);
                ensureEntitlements(user);
                created++;
                log.info("Created active account: {}", normalized);
            }
        }

        log.info("Demo accounts seed done: created={}, activated/updated={}", created, updated);
    }

    private void ensureEntitlements(User user) {
        assignDefaultWorkspacePlan(user);
        ensureWorkspaceSubscription(user);
        ensureFeedbackSubscriptionAndWallet(user);
    }

    private void assignDefaultWorkspacePlan(User user) {
        try {
            WorkspacePlan plan = workspacePlanService.getDefaultPlan();
            user.setWorkspacePlan(plan);
            try {
                user.setWorkspacePlanTier(WorkspacePlanTier.valueOf(plan.getCode()));
            } catch (IllegalArgumentException ignored) {
                user.setWorkspacePlanTier(WorkspacePlanTier.FREE_WORKSPACE);
            }
            userRepository.save(user);
        } catch (Exception e) {
            log.warn("Skip workspace plan for {}: {}", user.getEmail(), e.getMessage());
        }
    }

    private void ensureWorkspaceSubscription(User user) {
        try {
            boolean hasActive = workspaceSubscriptionRepository
                    .findByUserId(user.getId(), org.springframework.data.domain.Pageable.unpaged())
                    .stream()
                    .anyMatch(s -> s.getStatus() == SubscriptionStatus.ACTIVE);
            if (hasActive) {
                return;
            }
            WorkspacePlan freePlan = workspacePlanService.getDefaultPlan();
            workspaceSubscriptionRepository.save(WorkspaceSubscription.builder()
                    .user(user)
                    .workspacePlan(freePlan)
                    .startDate(LocalDateTime.now())
                    .endDate(LocalDateTime.now().plusYears(100))
                    .status(SubscriptionStatus.ACTIVE)
                    .build());
        } catch (Exception e) {
            log.warn("Skip workspace subscription for {}: {}", user.getEmail(), e.getMessage());
        }
    }

    private void ensureFeedbackSubscriptionAndWallet(User user) {
        Plan freePlan = planRepository.findByName("Free").orElse(null);
        long tokenLimit = freePlan != null ? freePlan.getTokenLimit() : 60_000L;

        if (walletRepository.findByUserId(user.getId()).isEmpty()) {
            walletRepository.save(Wallet.builder()
                    .user(user)
                    .tokenBalance(tokenLimit)
                    .totalRecharged(tokenLimit)
                    .build());
        }

        boolean hasActiveSub = !subscriptionRepository
                .findByUserIdAndStatus(user.getId(), SubscriptionStatus.ACTIVE)
                .isEmpty();
        if (!hasActiveSub && freePlan != null) {
            subscriptionRepository.save(Subscription.builder()
                    .user(user)
                    .plan(freePlan)
                    .startDate(LocalDateTime.now())
                    .endDate(LocalDateTime.now().plusYears(100))
                    .status(SubscriptionStatus.ACTIVE)
                    .build());
        }
    }

    private static String displayNameFromEmail(String email) {
        String local = email.substring(0, email.indexOf('@'));
        return local.replaceAll("[^a-zA-Z0-9]+", " ").trim();
    }
}
