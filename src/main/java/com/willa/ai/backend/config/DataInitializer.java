package com.willa.ai.backend.config;

import com.willa.ai.backend.entity.Plan;
import com.willa.ai.backend.entity.Subscription;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.Wallet;
import com.willa.ai.backend.entity.enums.BillingCycle;
import com.willa.ai.backend.entity.enums.Role;
import com.willa.ai.backend.entity.enums.SubscriptionStatus;
import com.willa.ai.backend.repository.PlanRepository;
import com.willa.ai.backend.repository.SubscriptionRepository;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
    public void run(String... args) throws Exception {
<<<<<<< Updated upstream
        log.info("Checking if default ADMIN user exists...");
        
        if (!userRepository.existsByEmail(adminEmail)) {
            User admin = User.builder()
                    .email(adminEmail)
                    .password(passwordEncoder.encode(adminPassword))
                    .fullName(adminName)
                    .role(Role.ADMIN)
                    .isActive(true)
                    .isEnabled(true)
                    .build();
            
            userRepository.save(admin);
            log.info("Created default ADMIN user successfully!");
            log.info("Email: {}", adminEmail);
            log.info("Password: {}", adminPassword);
        } else {
            log.info("Default ADMIN user already exists.");
        }

=======
>>>>>>> Stashed changes
    }

    private Plan initPlan(String name, String desc, BigDecimal price, BillingCycle cycle, Integer tokens) {
        return planRepository.findByName(name).orElseGet(() -> {
            Plan plan = Plan.builder()
                    .name(name)
                    .description(desc)
                    .price(price)
                    .billingCycle(cycle)
                    .tokenLimit(tokens)
                    .isActive(true)
                    .build();
            log.info("Creating plan: {}", name);
            return planRepository.save(plan);
        });
    }
}