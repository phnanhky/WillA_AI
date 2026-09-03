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
            "ngocanh2005@gmail.com",
            "minhthu2504@gmail.com",
            "phuonglinh2004@gmail.com",
            "thuytien2710@gmail.com",
            "hoangyen2005@gmail.com",
            "kimanh1608@gmail.com",
            "ngocmai2206@gmail.com",
            "quynhanh2004@gmail.com",
            "dieulinh1809@gmail.com",
            "thanhha2005@gmail.com",
            "trangnguyen2503@gmail.com",
            "huyenmy2004@gmail.com",
            "anhthu1207@gmail.com",
            "phuonganh2005@gmail.com",
            "ngocdiep2901@gmail.com",
            "thuyduong2004@gmail.com",
            "maiuyen1705@gmail.com",
            "khanhvy2005@gmail.com",
            "nhatminh2408@gmail.com",
            "hoanganh2004@gmail.com",
            "quynhchi1506@gmail.com",
            "minhngoc2005@gmail.com",
            "thanhtruc0911@gmail.com",
            "ngocphuong2004@gmail.com",
            "baongoc2307@gmail.com",
            "linhchi2005@gmail.com",
            "myhanh1402@gmail.com",
            "tuyetmai2004@gmail.com",
            "khanhngan2605@gmail.com",
            "thuyhanh2005@gmail.com",
            "ngoclinh0310@gmail.com",
            "phuongthao2004@gmail.com",
            "minhchau1908@gmail.com",
            "yenphuong2005@gmail.com",
            "kimngan2209@gmail.com",
            "nhungnguyen2004@gmail.com",
            "hoaimy1106@gmail.com",
            "thanhvy2005@gmail.com",
            "ngocquynh2803@gmail.com",
            "diepanh2004@gmail.com",
            "anhnguyen1707@gmail.com",
            "minhthuong2005@gmail.com",
            "thuytrang2401@gmail.com",
            "khanhlinh2004@gmail.com",
            "ngocuyen0905@gmail.com",
            "phuongnhi2005@gmail.com",
            "haianh2110@gmail.com",
            "mytam2004@gmail.com",
            "thanhngan1604@gmail.com",
            "ngocanh2908@gmail.com",
            "tramy2005@gmail.com",
            "quynhnhu1309@gmail.com",
            "hoangmai2004@gmail.com",
            "kimchi2507@gmail.com",
            "minhanh2005@gmail.com",
            "thuytien0206@gmail.com",
            "ngocvy2004@gmail.com",
            "phuongly1805@gmail.com",
            "khanhuyen2005@gmail.com",
            "anhthu2709@gmail.com",
            "minhnguyen2004@gmail.com",
            "baotrang1508@gmail.com",
            "thanhngoc2005@gmail.com",
            "ngocminh2304@gmail.com",
            "huyenanh2004@gmail.com",
            "maihoang1007@gmail.com",
            "phuonguyen2005@gmail.com",
            "khanhchi2802@gmail.com",
            "ngoclam2004@gmail.com",
            "thuyngan1906@gmail.com",
            "anhthuong2005@gmail.com",
            "minhthu1208@gmail.com",
            "quynhanh2004vn@gmail.com",
            "ngocdiep2505@gmail.com",
            "thanhvy2005vn@gmail.com",
            "hoanglinh1709@gmail.com",
            "kimanh2004@gmail.com",
            "phuongmai2207@gmail.com",
            "thuyduong2005@gmail.com",
            "ngocnhu1406@gmail.com",
            "minhchau2004@gmail.com",
            "anhkhoa1905@gmail.com",
            "khanhvy2005vn@gmail.com",
            "baongoc2808@gmail.com",
            "trangmy2004@gmail.com",
            "thanhha2306@gmail.com",
            "ngocanh2005vn@gmail.com",
            "phuongthao1704@gmail.com",
            "huyenmy2004vn@gmail.com",
            "kimngan2509@gmail.com",
            "linhngoc2005@gmail.com",
            "thuyhanh1203@gmail.com",
            "minhngoc2004@gmail.com",
            "anhthu2807@gmail.com",
            "ngocphuong2005@gmail.com",
            "quynhchi1609@gmail.com",
            "hoangyen2004@gmail.com",
            "phuonganh2305@gmail.com",
            "thuytrang2005@gmail.com",
            "khanhlinh1108@gmail.com",
            "ngocmai2004@gmail.com",
            "dieulinh2607@gmail.com",
            "baotrang2005@gmail.com",
            "minhanh1804@gmail.com",
            "thanhtruc2004@gmail.com",
            "ngocquynh2906@gmail.com",
            "phuonglinh2005@gmail.com",
            "myhanh2109@gmail.com",
            "tuyetmai2004vn@gmail.com",
            "khanhngan1507@gmail.com",
            "ngoclinh2005@gmail.com",
            "thuyduong2403@gmail.com",
            "minhthu2004@gmail.com",
            "anhnguyen2805@gmail.com",
            "hoaimy2005@gmail.com",
            "thanhvy1706@gmail.com",
            "ngocyen2004@gmail.com",
            "phuongnhi2508@gmail.com",
            "khanhuyen2005vn@gmail.com",
            "anhthu0904@gmail.com",
            "minhnguyen2005@gmail.com",
            "ngocvy2207@gmail.com",
            "thanhngan2004@gmail.com",
            "kimchi1308@gmail.com",
            "huyenanh2005@gmail.com",
            "phuonguyen1909@gmail.com",
            "baongoc2004@gmail.com",
            "thuytien2605@gmail.com",
            "ngocanh1502@gmail.com",
            "minhchau2005@gmail.com",
            "quynhnhu2408@gmail.com",
            "thanhha1107@gmail.com",
            "ngocdiep2005@gmail.com",
            "phuongmai2909@gmail.com",
            "khanhchi2004@gmail.com",
            "linhchi2305@gmail.com",
            "anhthu2005@gmail.com",
            "ngocminh1708@gmail.com",
            "thuyhanh2004@gmail.com",
            "minhthuong2606@gmail.com",
            "hoanganh2005@gmail.com",
            "quynhanh1209@gmail.com",
            "thanhngoc2807@gmail.com",
            "kimanh2005@gmail.com",
            "phuongthao1608@gmail.com"
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
