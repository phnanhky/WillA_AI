package com.willa.ai.backend.config;

import com.willa.ai.backend.entity.Plan;
import com.willa.ai.backend.entity.Subscription;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.enums.BillingCycle;
import com.willa.ai.backend.entity.enums.SubscriptionStatus;
import com.willa.ai.backend.repository.PlanRepository;
import com.willa.ai.backend.repository.SubscriptionRepository;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-off: nâng {@link #TARGET_EMAIL} lên Pro Feedback MONTHLY.
 * Hủy gói recurring đang active (Free/Student/Pro cũ) rồi kích hoạt Pro + cộng token.
 * Chạy xong trên VPS → xóa {@code @Component} hoặc cả class này.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {
    @Override
    @Transactional
    public void run(String... args) {
    }
}
