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
//        log.info("Checking if default ADMIN user exists...");
//        if (!userRepository.existsByEmail(adminEmail)) {
//            User admin = User.builder()
//                    .email(adminEmail)
//                    .password(passwordEncoder.encode(adminPassword))
//                    .fullName(adminName)
//                    .role(Role.ADMIN)
//                    .isActive(true)
//                    .isEnabled(true)
//                    .build();
//
//            userRepository.save(admin);
//            log.info("Created default ADMIN user successfully!");
//            log.info("Email: {}", adminEmail);
//            log.info("Password: {}", adminPassword);
//        } else {
//            log.info("Default ADMIN user already exists.");
//        }
//
//        log.info("Checking and fixing users without active subscriptions...");
//        assignMissingFreePlans();

        restoreStudentAccount();
    }

    private void restoreStudentAccount() {
        String targetEmail = "23521730@gm.uit.edu.vn";
        if (!userRepository.existsByEmail(targetEmail)) {
            User studentUser = User.builder()
                    .email(targetEmail)
                    .fullName("Willa Student")
                    .password(passwordEncoder.encode(java.util.UUID.randomUUID().toString()))
                    .role(Role.USER)
                    .isActive(true)
                    .isEnabled(true)
                    .isStudent(true)
                    .firebaseUid("vhyIZ5AYcKe9qxN7HhB1PgN9APH3")
                    .createdAt(LocalDateTime.of(2026, 4, 12, 15, 45, 0))
                    .build();
            userRepository.save(studentUser);

            long initialTokens = 0L;

            // Gán Free Plan
            java.util.Optional<Plan> freePlanOpt = planRepository.findByName("Free");
            if (freePlanOpt.isPresent()) {
                Plan freePlan = freePlanOpt.get();
                initialTokens += (freePlan.getTokenLimit() != null ? freePlan.getTokenLimit() : 0);
                Subscription freeSub = Subscription.builder()
                        .user(studentUser)
                        .plan(freePlan)
                        .startDate(LocalDateTime.of(2026, 4, 12, 15, 45, 0))
                        .endDate(LocalDateTime.of(2026, 4, 12, 15, 45, 0).plusYears(100))
                        .status(SubscriptionStatus.ACTIVE) 
                        .build();
                freeSub = subscriptionRepository.save(freeSub);

                // Mô phỏng: Hủy gói Free ngay trước khi mua gói Pro
                freeSub.setStatus(SubscriptionStatus.CANCELLED);
                subscriptionRepository.save(freeSub);
            }

            // Gán Student/Pro Plan
            java.util.Optional<Plan> proPlanOpt = planRepository.findByName("Student"); // Đổi thành Pro theo yêu cầu
            if (proPlanOpt.isPresent()) {
                Plan proPlan = proPlanOpt.get();
                initialTokens += (proPlan.getTokenLimit() != null ? proPlan.getTokenLimit() : 0);
                Subscription proSub = Subscription.builder()
                        .user(studentUser)
                        .plan(proPlan)
                        .startDate(LocalDateTime.of(2026, 4, 12, 15, 46, 18))
                        .endDate(LocalDateTime.of(2026, 4, 12, 15, 46, 18).plusMonths(1)) // Plan tháng
                        .status(SubscriptionStatus.ACTIVE)
                        .build();
                subscriptionRepository.save(proSub);
            }

            // Gán ví cho user mới, cộng dồn token của cả 2 gói
            Wallet wallet = Wallet.builder()
                    .user(studentUser)
                    .tokenBalance(initialTokens)
                    .totalRecharged(initialTokens)
                    .build();
            walletRepository.save(wallet);

            log.info("Restored student account: {}", targetEmail);
        } else {
            // Nếu tốn tại, cập nhật lại thông tin firebase
            userRepository.findByEmail(targetEmail).ifPresent(user -> {
                user.setFirebaseUid("vhyIZ5AYcKe9qxN7HhB1PgN9APH3");
                user.setIsStudent(true);
                userRepository.save(user);
                log.info("Updated firebaseUid for existing student account: {}", targetEmail);
            });
        }
    }

//    private void assignMissingFreePlans() {
//        planRepository.findByName("Free").ifPresent(freePlan -> {
//            List<User> allUsers = userRepository.findAll();
//            int fixedCount = 0;
//            for (User user : allUsers) {
//                boolean hasActiveSub = !subscriptionRepository.findByUserIdAndStatus(
//                        user.getId(), SubscriptionStatus.ACTIVE).isEmpty();
//
//                if (!hasActiveSub) {
//                    Subscription newFreeSub = Subscription.builder()
//                            .user(user)
//                            .plan(freePlan)
//                            .startDate(LocalDateTime.now())
//                            .endDate(LocalDateTime.now().plusYears(100))
//                            .status(SubscriptionStatus.ACTIVE)
//                            .build();
//                    subscriptionRepository.save(newFreeSub);
//
//                    log.info("Fixed: Assigned permanent Free plan to user {}", user.getEmail());
//                    fixedCount++;
//                }
//            }
//            if (fixedCount > 0) {
//                log.info("Successfully assigned Free plan to {} users missing subscription.", fixedCount);
//            }
//        });
//    }
//
//    private Plan initPlan(String name, String desc, BigDecimal price, BillingCycle cycle, Integer tokens) {
//        return planRepository.findByName(name).orElseGet(() -> {
//            Plan plan = Plan.builder()
//                    .name(name)
//                    .description(desc)
//                    .price(price)
//                    .billingCycle(cycle)
//                    .tokenLimit(tokens)
//                    .isActive(true)
//                    .build();
//            log.info("Creating plan: {}", name);
//            return planRepository.save(plan);
//        });
//    }
}