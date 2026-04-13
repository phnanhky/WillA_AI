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
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

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
        restoreStudentAccount();
    }

    public void restoreStudentAccount() {
        String targetEmail = "23521730@gm.uit.edu.vn";
        
        java.util.Optional<User> userOpt = userRepository.findByEmail(targetEmail);
        if (userOpt.isPresent()) {
            User studentUser = userOpt.get();

            // Gán Gói Student nếu chưa có
            boolean hasStudentSub = subscriptionRepository.findAll().stream()
                    .anyMatch(s -> s.getUser().getId().equals(studentUser.getId()) &&
                            s.getPlan().getName().equals("Student") &&
                            s.getStatus() == SubscriptionStatus.ACTIVE);

            if (!hasStudentSub) {
                java.util.Optional<Plan> studentPlanOpt = planRepository.findByName("Student")
                        .filter(Plan::getIsActive);

                if (studentPlanOpt.isPresent()) {
                    Plan studentPlan = studentPlanOpt.get();
                    Subscription studentSub = Subscription.builder()
                            .user(studentUser)
                            .plan(studentPlan)
                            .startDate(LocalDateTime.of(2026, 4, 12, 15, 46, 18))
                            .endDate(LocalDateTime.of(2026, 4, 12, 15, 46, 18).plusMonths(1)) // Student plan 1 tháng
                            .status(SubscriptionStatus.ACTIVE)
                            .build();
                    subscriptionRepository.save(studentSub);
                    log.info("Bắt đầu khởi tạo gói Student (Monthly) cho tài khoản: {}", targetEmail);
                } else {
                    log.warn("Không tìm thấy gói Student nào với chu kỳ BillingCycle.MONTHLY");
                }
            } else {
                log.info("Tài khoản {} đã có gói Student Monthly", targetEmail);
            }

            // Set thẳng 70000 tokens vào ví
            long targetTokens = 70000L;
            walletRepository.findByUserId(studentUser.getId()).ifPresentOrElse(wallet -> {
                wallet.setTokenBalance(targetTokens);
                wallet.setTotalRecharged(targetTokens);
                walletRepository.save(wallet);
                log.info("Cập nhật thẳng {} token vào ví của {}", targetTokens, targetEmail);
            }, () -> {
                Wallet newWallet = Wallet.builder()
                        .user(studentUser)
                        .tokenBalance(targetTokens)
                        .totalRecharged(targetTokens)
                        .build();
                walletRepository.save(newWallet);
                log.info("Tạo mới ví với {} token cho {}", targetTokens, targetEmail);
            });
        }
    }
}
