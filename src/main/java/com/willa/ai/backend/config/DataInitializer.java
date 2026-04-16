package com.willa.ai.backend.config;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

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
        upgradeToProAccounts();
    }

    public void upgradeToProAccounts() {
        List<String> targetEmails = List.of("lethithuanhieu2019@gmail.com", "tncamtien1712@gmail.com");

        // Lấy gói Pro Monthly active
        java.util.Optional<Plan> proPlanOpt = planRepository.findAll().stream()
                .filter(p -> p.getName().toLowerCase().contains("pro") && p.getBillingCycle() == BillingCycle.MONTHLY && Boolean.TRUE.equals(p.getIsActive()))
                .findFirst();

        if (proPlanOpt.isEmpty()) {
            log.warn("Không tìm thấy gói Pro Monthly nào đang Active!");
            return;
        }

        Plan proPlan = proPlanOpt.get();
        // Lấy số lượng tokens của gói Pro, mặc định 5M token nếu null
        long addedTokens = proPlan.getTokenLimit();

        for (String email : targetEmails) {
            java.util.Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isPresent()) {
                User user = userOpt.get();

                // Expire all old active subscriptions for this user
                List<Subscription> activeSubs = subscriptionRepository.findAll().stream()
                        .filter(s -> s.getUser().getId().equals(user.getId()) && s.getStatus() == SubscriptionStatus.ACTIVE)
                        .collect(Collectors.toList());

                for (Subscription sub : activeSubs) {
                    sub.setStatus(SubscriptionStatus.EXPIRED);
                    subscriptionRepository.save(sub);
                    log.info("Đã EXPIRED sub cũ của: {}", email);
                }

                // Add new Pro subscription
                Subscription newProSub = Subscription.builder()
                        .user(user)
                        .plan(proPlan)
                        .startDate(LocalDateTime.now())
                        .endDate(LocalDateTime.now().plusMonths(1))
                        .status(SubscriptionStatus.ACTIVE)
                        .build();
                subscriptionRepository.save(newProSub);
                log.info("Đã tạo mới gói Pro (Monthly) cho: {}", email);

                // Add to wallet OR create new wallet
                walletRepository.findByUserId(user.getId()).ifPresentOrElse(wallet -> {
                    // Cộng dồn token của gói mới
                    wallet.setTokenBalance(wallet.getTokenBalance() + addedTokens);
                    wallet.setTotalRecharged(wallet.getTotalRecharged() + addedTokens);
                    walletRepository.save(wallet);
                    log.info("Đã cộng thêm {} token vào ví của {}", addedTokens, email);
                }, () -> {
                    Wallet newWallet = Wallet.builder()
                            .user(user)
                            .tokenBalance(addedTokens)
                            .totalRecharged(addedTokens)
                            .build();
                    walletRepository.save(newWallet);
                    log.info("Đã tạo mới ví với {} token cho {}", addedTokens, email);
                });
            } else {
                log.warn("Không tìm thấy user với email: {}", email);
            }
        }
    }
}
