package com.willa.ai.backend.config;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.CommandLineRunner;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

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
    "quanghuynguyn@gmail.com",
    "minhducng@gmail.com",
    "hoangnamn@gmail.com",
    "tuananhtn@gmail.com",
    "ducminhtt@gmail.com",
    "thanhdat2004@gmail.com",
    "ngocanhtran@gmail.com",
    "minhquanle2010@gmail.com",
    "quocbaonguyen2212@gmail.com",
    "anhkhoanguyen@gmail.com",
    "thanhsonlt@gmail.com",
    "hoanglongtt@gmail.com",
    "nhatminh234@gmail.com",
    "tunglamphn@gmail.com",
    "manhcuong122@gmail.com",
    "thuytienl@gmail.com",
    "duongminh@gmail.com",
    "viettanhh@gmail.com",
    "kimanh324@gmail.com",
    "baole56@gmail.com",
    "trungkien@gmail.com",
    "ngocminh@gmail.com",
    "hoangvietng@gmail.com",
    "thanhdatle12@gmail.com",
    "linhchinguyen78@gmail.com",
    "quangminhtr69@gmail.com",
    "huynhducngn@gmail.com",
    "yenphuong@gmail.com",
    "tienphat88@gmail.com",
    "ducanhtrinh@gmail.com",
    "nguyenhoangcao@gmail.com",
    "thanhphongman@gmail.com",
    "minhduclevo23@gmail.com",
    "quocviettran12@gmail.com",
    "haianhngn@gmail.com",
    "tuanminh12@gmail.com",
    "hoangphuc16@gmail.com",
    "nhatnamtran12@gmail.com",
    "khangduy.nguyen@gmail.com",
    "thienanle35@gmail.com",
    "minhhoangg@gmail.com",
    "quocanhng@gmail.com",
    "thanvinh@gmail.com",
    "baokhanhfc@gmail.com",
    "ducphatt@gmail.com",
    "minhthangng@gmail.com",
    "anhdyle@gmail.com",
    "trongnghiahotran@gmail.com",
    "quanghy@gmail.com",
    "minhatt@gmail.com",
    "thanbinh@gmail.com",
    "phucanh@gmail.com",
    "tuanphongn@gmail.com",
    "ngocsont@gmail.com",
    "hoangduyngn@gmail.com",
    "vietkhangl@gmail.com",
    "thanhhaingn@gmail.com",
    "anhminh@gmail.com",
    "nhatquangle@gmail.com",
    "tienminhnguyen@gmail.com",
    "hoangsontran@gmail.com",
    "khanhphucn@gmail.com",
    "mintri5@gmail.com",
    "baoduytran@gmail.com",
    "thanhphucnguyen@gmail.com",
    "quangvinhle@gmail.com",
    "nguyenminhtran@gmail.com",
    "tuananhle@gmail.com",
    "hoanglongnguyen@gmail.com",
    "anhkiet278@gmail.com",
    "minhsonlework@gmail.com",
    "ducvietnguyenfd@gmail.com",
    "thanhngantran234@gmail.com",
    "quocminhnguyen3@gmail.com",
    "phuonganhle@gmail.com",
    "hoangthientn@gmail.com",
    "khanhminhngn@gmail.com",
    "minhphucle@gmail.com",
    "thanhduytran@gmail.com",
    "quangnamnn@gmail.com",
    "ducson@gmail.com",
    "hoangminh@gmail.com",
    "vietduy@gmail.com",
    "anhkhoa@gmail.com",
    "minhnguyen@gmail.com",
    "thanmintran@gmail.com",
    "quocduy23@gmail.com",
    "hoangphongtv@gmail.com",
    "tuanminh6453@gmail.com",
    "ducanh2342@gmail.com",
    "ngocminh742@gmail.com",
    "quocanht12@gmail.com",
    "minhtringuyen1604@gmail.com"
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
