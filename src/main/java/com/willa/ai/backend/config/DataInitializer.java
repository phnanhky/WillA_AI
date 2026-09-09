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
    "nguyenminhquan2004@gmail.com",
    "tranquocdat05@gmail.com",
    "leminhhuy2006@gmail.com",
    "phamgiahan07@gmail.com",
    "vothanhtrungg05@gmail.com",
    "dangngoclam2004@gmail.com",
    "buiducanh06@gmail.com",
    "dinhquanghuy2007@gmail.com",
    "dohoangnam04@gmail.com",
    "hominhkhoa05@gmail.com",
    "ngothanhson2006@gmail.com",
    "duongminhtri07@gmail.com",
    "huynhbaolong04@gmail.com",
    "caominhduc2005@gmail.com",
    "lyphuonganh06@gmail.com",
    "maihoanglong2007@gmail.com",
    "tathanhhuy04@gmail.com",
    "phananhkhoa05@gmail.com",
    "trinhquocviet2006@gmail.com",
    "haminhquan07@gmail.com",
    "binnguyen2004@gmail.com",
    "bongle05@gmail.com",
    "tittran2006@gmail.com",
    "bapnguyen07@gmail.com",
    "susuhoang04@gmail.com",
    "meomeo2005@gmail.com",
    "cungnguyen06@gmail.com",
    "tommytran07@gmail.com",
    "khoailang04@gmail.com",
    "banhbao2006@gmail.com",
    "bapbap05@gmail.com",
    "kemle07@gmail.com",
    "sushihoang04@gmail.com",
    "bunbohue06@gmail.com",
    "banhmi2007@gmail.com",
    "cakho05@gmail.com",
    "traicay04@gmail.com",
    "bimtran06@gmail.com",
    "bonbonnguyen07@gmail.com",
    "bunnyle05@gmail.com",
    "nguoibuon2004@gmail.com",
    "haycuoi05@gmail.com",
    "luoibieng2006@gmail.com",
    "nguoihayquen07@gmail.com",
    "thichngung04@gmail.com",
    "meongu2005@gmail.com",
    "haman06@gmail.com",
    "nguoivui07@gmail.com",
    "khongthichon04@gmail.com",
    "haymet2006@gmail.com",
    "thichimlang05@gmail.com",
    "nguoilangthang07@gmail.com",
    "nhoocon04@gmail.com",
    "ngocnghech2005@gmail.com",
    "becon06@gmail.com",
    "cuncon2007@gmail.com",
    "meocon04@gmail.com",
    "nhocnguyen06@gmail.com",
    "bebehoang05@gmail.com",
    "cunbong07@gmail.com",
    "minhhuy2004@gmail.com",
    "tuananhh05@gmail.com",
    "quanghuyy06@gmail.com",
    "ngocanhh07@gmail.com",
    "thanhhaai04@gmail.com",
    "minhtrii05@gmail.com",
    "ducminhh2006@gmail.com",
    "hoangnamm07@gmail.com",
    "khanhlinhh04@gmail.com",
    "anhthuu05@gmail.com",
    "minhthuu06@gmail.com",
    "quocbaoo07@gmail.com",
    "nhatminhh04@gmail.com",
    "thanhduyy06@gmail.com",
    "baolongg05@gmail.com",
    "ngocminhh07@gmail.com",
    "phuonganhh04@gmail.com",
    "tuanminhh05@gmail.com",
    "hoangduyy06@gmail.com",
    "anhkiett07@gmail.com",
    "nguyenhoangminh04@gmail.com",
    "tranminhduc05@gmail.com",
    "lequanghuy06@gmail.com",
    "phamnhatminh07@gmail.com",
    "vobaolong04@gmail.com",
    "dangminhtri05@gmail.com",
    "buiquocanh06@gmail.com",
    "dinhthanhdat07@gmail.com",
    "dophuongnam04@gmail.com",
    "hominhduc05@gmail.com",
    "ngothienan06@gmail.com",
    "duongquocbao07@gmail.com",
    "huynhminhhoang04@gmail.com",
    "caothanhphuc05@gmail.com",
    "lyquangnam06@gmail.com",
    "maiminhson07@gmail.com",
    "tanguyenhoang04@gmail.com",
    "phanminhthang05@gmail.com",
    "trinhbaokhanh06@gmail.com",
    "haduongminh07@gmail.com",
    "binbeo2004@gmail.com",
    "bapbap05@gmail.com",
    "titbeo06@gmail.com",
    "cunmap07@gmail.com",
    "meocon2004@gmail.com",
    "bongbong05@gmail.com"
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
