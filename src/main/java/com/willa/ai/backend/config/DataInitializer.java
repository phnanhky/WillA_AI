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
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * One-off seed accounts, mỗi account cách nhau 5–60 giây (random).
 * Password: {@code 123456789}. Deploy xong → xóa {@code @Component} hoặc làm rỗng {@link #run}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private static final String PLAIN_PASSWORD = "123456789";

    private static final List<String> EMAILS = List.of(
            "vvut82@gmail.com",
            "trangmt902@gmail.com",
            "thaovtt1970@gmail.com",
            "duongnsy278@gmail.com",
            "nguyenxphuoc1969@gmail.com",
            "haliennguyen167@gmail.com",
            "huynvp1995@gmail.com",
            "trunghuynhmai0@gmail.com",
            "toanpnd@gmail.com",
            "quangghuyy0907@gmail.com",
            "anhnt2936@gmail.com",
            "nnpnguyen04@gmail.com",
            "quandm279@gmail.com",
            "phuctnh02@gmail.com",
            "vtanhai97@gmail.com",
            "tienthuann95@gmail.com",
            "duyphnguyen03@gmail.com",
            "hathotranne@gmail.com",
            "embengooem@gmail.com",
            "vuhoanglongg00@gmail.com",
            "sieucapvutrupro@gmail.com",
            "binhbaobinhbin@gmail.com",
            "loinguyenvr2@gmail.com",
            "chaunvn09@gmail.com",
            "haileyyphm@gmail.com",
            "hoangyenlopp@gmail.com",
            "anz12ann@gmail.com",
            "congiomuahoa@gmail.com",
            "quynhanhshynn07@gmail.com",
            "giangtq522@gmail.com",
            "thicandwa@gmail.com",
            "hanigiahan9@gmail.com",
            "anhthytranthi01@gmail.com",
            "tramlnp6@gmail.com",
            "tuonggiangbichthi@gmail.com",
            "ldbvya11@gmail.com",
            "andaiduongg@gmail.com",
            "thienkimtranthai02@gmail.com",
            "tramttb173@gmail.com",
            "ngosyvuong05@gmail.com",
            "ngosyhuan03@gmail.com",
            "lietvuongnnguyen@gmail.com",
            "lelalaryy1@gmail.com"
    );

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final WorkspacePlanService workspacePlanService;
    private final WorkspaceSubscriptionRepository workspaceSubscriptionRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void run(String... args) {
        String encoded = passwordEncoder.encode(PLAIN_PASSWORD);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "data-init-staggered");
            t.setDaemon(true);
            return t;
        });

        long delaySeconds = 0L;
        for (int i = 0; i < EMAILS.size(); i++) {
            final String email = EMAILS.get(i);
            final int index = i + 1;
            final long runAt = delaySeconds;
            scheduler.schedule(() -> {
                try {
                    transactionTemplate.executeWithoutResult(status -> {
                        upsertActiveAccount(email, encoded);
                        log.info("Init account {}/{} done (delay={}s): {}", index, EMAILS.size(), runAt, email);
                    });
                } catch (Exception e) {
                    log.error("Init failed for {}: {}", email, e.getMessage(), e);
                } finally {
                    if (index == EMAILS.size()) {
                        scheduler.shutdown();
                    }
                }
            }, delaySeconds, TimeUnit.SECONDS);

            if (i < EMAILS.size() - 1) {
                delaySeconds += ThreadLocalRandom.current().nextInt(5, 61); // 5–60s inclusive
            }
        }

        log.info("Scheduled staggered init for {} accounts (5–60s random gaps)", EMAILS.size());
    }

    private void upsertActiveAccount(String email, String encodedPassword) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        Optional<User> existing = userRepository.findByEmail(normalized);
        if (existing.isPresent()) {
            User user = existing.get();
            user.setPassword(encodedPassword);
            user.setIsEnabled(true);
            user.setIsActive(true);
            user.setVerificationToken(null);
            userRepository.save(user);
            ensureEntitlements(user);
            log.info("Activated existing account: {}", normalized);
            return;
        }

        User user = User.builder()
                .email(normalized)
                .fullName(displayNameFromEmail(normalized))
                .password(encodedPassword)
                .role(Role.USER)
                .isEnabled(true)
                .isActive(true)
                .isStudent(false)
                .requiresReview(false)
                .workspacePlanTier(WorkspacePlanTier.FREE_WORKSPACE)
                .build();
        user = userRepository.save(user);
        ensureEntitlements(user);
        log.info("Created active account: {}", normalized);
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
                    .findByUserId(user.getId(), Pageable.unpaged())
                    .getContent()
                    .stream()
                    .anyMatch(ws -> ws.getStatus() == SubscriptionStatus.ACTIVE);
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
