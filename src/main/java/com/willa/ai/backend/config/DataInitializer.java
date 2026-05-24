package com.willa.ai.backend.config;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.boot.CommandLineRunner;
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

/**
 * Seed tối thiểu khi startup — không xóa subscription/user, không reset toàn DB.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final WalletRepository walletRepository;

    @Override
    @Transactional
    public void run(String... args) {
    }
}
