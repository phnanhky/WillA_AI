package com.willa.ai.backend.config;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.willa.ai.backend.entity.enums.Gender;
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
        upgradeSpecificUsers();
    }

    private void upgradeSpecificUsers() {
        log.info("Starting subscription data migration for specific users...");
        
        upgradeUser("nguyennguyen17032004@gmail.com", "Pro", 0);
        upgradeUser("nguyennxhhe189017@fpt.edu.vn", "Student", 0); // Bonus tokens on top
        
        log.info("Finished subscription data migration.");
    }
    
    private void upgradeUser(String email, String planKeyword, int bonusTokens) {
        userRepository.findByEmail(email).ifPresentOrElse(user -> {
            log.info("Found user {}. Upgrading to {}...", email, planKeyword);
            
            // 1. Expire existing active subscriptions
            List<Subscription> activeSubs = subscriptionRepository.findByUserIdAndStatus(user.getId(), SubscriptionStatus.ACTIVE);
            for (Subscription sub : activeSubs) {
                sub.setStatus(SubscriptionStatus.EXPIRED);
            }
            subscriptionRepository.saveAll(activeSubs);

            // 2. Find target plan
            Plan targetPlan = planRepository.findAll().stream()
                    .filter(p -> p.getName().toLowerCase().contains(planKeyword.toLowerCase()))
                    .findFirst()
                    .orElse(null);

            if (targetPlan != null) {
                // 3. Create new subscription
                Subscription newSub = Subscription.builder()
                        .user(user)
                        .plan(targetPlan)
                        .status(SubscriptionStatus.ACTIVE)
                        .startDate(LocalDateTime.now())
                        .endDate(LocalDateTime.now().plusMonths(1))
                        .build();
                subscriptionRepository.save(newSub);
                
                // 4. Recharge Wallet
                walletRepository.findByUserId(user.getId()).ifPresentOrElse(wallet -> {
                    long addedTokens = targetPlan.getTokenLimit() + bonusTokens;
                    wallet.setTokenBalance(wallet.getTokenBalance() + addedTokens);
                    wallet.setTotalRecharged(wallet.getTotalRecharged() + addedTokens);
                    walletRepository.save(wallet);
                }, () -> {
                    long initialTokens = targetPlan.getTokenLimit() + bonusTokens;
                    Wallet newWallet = Wallet.builder()
                            .user(user)
                            .tokenBalance(initialTokens)
                            .totalRecharged(initialTokens)
                            .build();
                    walletRepository.save(newWallet);
                });
                
                log.info("Successfully upgraded {} to {} plan and updated wallet.", email, planKeyword);
            } else {
                log.warn("Plan containing keyword '{}' not found in database.", planKeyword);
            }
        }, () -> log.warn("User with email {} not found.", email));
    }
}
