package com.willa.ai.backend.config;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.willa.ai.backend.entity.Plan;
import com.willa.ai.backend.entity.Subscription;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.Wallet;
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
        cleanupSubscriptions();
        updateUsersStatus();
    }

    private void updateUsersStatus() {
        log.info("Updating all users to isEnabled=true and isActive=true...");
        List<User> users = userRepository.findAll();
        for (User user : users) {
            boolean changed = false;
            if (user.getIsEnabled() == null || !user.getIsEnabled()) {
                user.setIsEnabled(true);
                changed = true;
            }
            if (user.getIsActive() == null || !user.getIsActive()) {
                user.setIsActive(true);
                changed = true;
            }
            if (changed) {
                userRepository.save(user);
                log.info("Updated user {} (ID: {}) status to enabled and active.", user.getEmail(), user.getId());
            }
        }
    }

    private void cleanupSubscriptions() {
        log.info("Cleaning up subscriptions...");
        List<Long> idsToDelete = List.of(419L);
        for (Long id : idsToDelete) {
            subscriptionRepository.findById(id).ifPresent(sub -> {
                subscriptionRepository.delete(sub);
                log.info("Deleted subscription ID: {}", id);
            });
        }
    }
}
