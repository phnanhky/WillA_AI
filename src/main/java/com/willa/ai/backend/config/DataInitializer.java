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
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One-off seed accounts, mỗi account cách nhau 5–60 giây (random).
 * {@code created_at} bắt đầu 26/08/2026 13:00 (Asia/Ho_Chi_Minh), rồi cộng dồn gap như logic cũ.
 * Password: {@code 123456789}. Deploy xong → xóa {@code @Component} hoặc làm rỗng {@link #run}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private static final String PLAIN_PASSWORD = "123456789";
    private static final ZoneId SEED_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    /** Mốc tạo account đầu tiên (26/08/2026 13:00). */
    private static final LocalDateTime SEED_START = LocalDateTime.of(2026, 8, 26, 13, 0, 0);

    private static final List<String> EMAILS = List.of(
    "nynic17012004@gmail.com",
    "finnieart17@gmail.com",
    "nguyenphamhoangn@gmail.com",
    "hoangvy26052005@gmail.com",
    "2253801013015@email.hcmulaw.edu.vn",
    "giabaonguyenngoc1510@gmail.com",
    "chihoihs47a1.ulaw@gmail.com",
    "nganngowr@gmail.com",
    "kimhangannguyen@gmail.com",
    "itzgametimer@gmail.com",
    "22130111@student.hcmus.edu.vn",
    "loken97512@robustq.com",
    "nichayrade2605@gmail.com",
    "nguyennguyen17032004@gmail.com",
    "tranquanghuyvn94@gmail.com",
    "phamthuhuong1992x@gmail.com",
    "maibuithithanh95@gmail.com",
    "dovanhaimail88@gmail.com",
    "vuhoanglong2k1vn@gmail.com",
    "trinhngocanhv95@gmail.com",
    "tuanhoangminh1998z@gmail.com",
    "khanhphanquoc797@gmail.com",
    "dangthithuhavn93@gmail.com",
    "ngoducthangwork88@gmail.com",
    "yenduonghai1990x@gmail.com",
    "khoivuongdinh96@gmail.com",
    "toquangvinh2000vn@gmail.com",
    "bachdaoxuanmail98@gmail.com",
    "trangdinhthu1991z@gmail.com",
    "luongvantoanv85@gmail.com"
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
        long offsetSeconds = 0L;
        for (int i = 0; i < EMAILS.size(); i++) {
            String email = EMAILS.get(i);
            int index = i + 1;
            LocalDateTime createdAt = SEED_START.plusSeconds(offsetSeconds);
            try {
                final long runAt = offsetSeconds;
                transactionTemplate.executeWithoutResult(status -> {
                    upsertActiveAccount(email, encoded, createdAt);
                    log.info(
                            "Init account {}/{} createdAt={} (offset={}s): {}",
                            index,
                            EMAILS.size(),
                            createdAt,
                            runAt,
                            email);
                });
            } catch (Exception e) {
                log.error("Init failed for {}: {}", email, e.getMessage(), e);
            }
            if (i < EMAILS.size() - 1) {
                offsetSeconds += ThreadLocalRandom.current().nextInt(5, 61); // 5–60s inclusive
            }
        }
        log.info(
                "Staggered init for {} accounts from {} {} (5–60s random gaps)",
                EMAILS.size(),
                SEED_START,
                SEED_ZONE);
    }

    private void upsertActiveAccount(String email, String encodedPassword, LocalDateTime createdAt) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        Optional<User> existing = userRepository.findByEmail(normalized);
        if (existing.isPresent()) {
            User user = existing.get();
            user.setPassword(encodedPassword);
            user.setIsEnabled(true);
            user.setIsActive(true);
            user.setVerificationToken(null);
            userRepository.save(user);
            userRepository.overwriteCreatedAt(user.getId(), createdAt);
            ensureEntitlements(user, createdAt);
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
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
        user = userRepository.save(user);
        ensureEntitlements(user, createdAt);
        log.info("Created active account: {}", normalized);
    }

    private void ensureEntitlements(User user, LocalDateTime createdAt) {
        assignDefaultWorkspacePlan(user);
        ensureWorkspaceSubscription(user, createdAt);
        ensureFeedbackSubscriptionAndWallet(user, createdAt);
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

    private void ensureWorkspaceSubscription(User user, LocalDateTime createdAt) {
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
                    .startDate(createdAt)
                    .endDate(createdAt.plusYears(100))
                    .status(SubscriptionStatus.ACTIVE)
                    .build());
        } catch (Exception e) {
            log.warn("Skip workspace subscription for {}: {}", user.getEmail(), e.getMessage());
        }
    }

    private void ensureFeedbackSubscriptionAndWallet(User user, LocalDateTime createdAt) {
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
                    .startDate(createdAt)
                    .endDate(createdAt.plusYears(100))
                    .status(SubscriptionStatus.ACTIVE)
                    .build());
        }
    }

    private static String displayNameFromEmail(String email) {
        String local = email.substring(0, email.indexOf('@'));
        return local.replaceAll("[^a-zA-Z0-9]+", " ").trim();
    }
}
