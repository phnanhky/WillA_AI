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
        enableAllUsers();
    }

    private void enableAllUsers() {
        log.info("Setting isEnabled = true for all users...");
        List<User> allUsers = userRepository.findAll();
        boolean updated = false;
        for (User user : allUsers) {
            if (user.getIsEnabled() == null || !user.getIsEnabled()) {
                user.setIsEnabled(true);
                updated = true;
            }
        }
        if (updated) {
            userRepository.saveAll(allUsers);
            log.info("Finished updating all users to isEnabled = true.");
        } else {
            log.info("All users are already enabled.");
        }
    }
}
