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
        
        java.util.Optional<User> userOpt = userRepository.findByEmail(targetEmail);
        
        if (userOpt.isPresent()) {
            User studentUser = userOpt.get();

            long addedTokens = 0L;

            Optional<Plan> plan = planRepository.findByName("Free");
            addedTokens += plan.get().getTokenLimit();
            // Gán Gói Student Monthly nếu chưa có
            boolean hasStudentSub = subscriptionRepository.findAll().stream()
                    .anyMatch(s -> s.getUser().getId().equals(studentUser.getId()) &&
                            s.getPlan().getName().toLowerCase().contains("Student") &&
                            s.getPlan().getBillingCycle() == BillingCycle.MONTHLY);


            if (!hasStudentSub) {
                java.util.Optional<Plan> studentMonthlyPlanOpt = planRepository.findAll().stream()
                        .filter(p -> p.getName().toLowerCase().contains("Student") && p.getBillingCycle() == BillingCycle.MONTHLY)
                        .findFirst();

                if (studentMonthlyPlanOpt.isPresent()) {
                    Plan studentPlan = studentMonthlyPlanOpt.get();
                    addedTokens += (studentPlan.getTokenLimit() != null ? studentPlan.getTokenLimit() : 0);
                    
                    Subscription studentSub = Subscription.builder()
                            .user(studentUser)
                            .plan(studentPlan)
                            .startDate(LocalDateTime.of(2026, 4, 12, 15, 46, 18))
                            .endDate(LocalDateTime.of(2026, 4, 12, 15, 46, 18).plusMonths(1)) // Student plan 1 tháng
                            .status(SubscriptionStatus.ACTIVE)
                            .build();
                    subscriptionRepository.save(studentSub);
                    log.info("Bắt đầu khởi tạo gói Student (Monthly) cho tài khoản: {}", targetEmail);

                    // Cập nhật ví, cộng dồn token
                    final long finalTokens = addedTokens;
                    walletRepository.findByUserId(studentUser.getId()).ifPresentOrElse(wallet -> {
                        wallet.setTokenBalance( finalTokens);
                        wallet.setTotalRecharged(finalTokens);
                        walletRepository.save(wallet);
                        log.info("Cộng dồn {} token gói Student vào ví đã có của {}", finalTokens, targetEmail);
                    }, () -> {
                        Wallet newWallet = Wallet.builder()
                                .user(studentUser)
                                .tokenBalance(finalTokens)
                                .totalRecharged(finalTokens)
                                .build();
                        walletRepository.save(newWallet);
                    });
                } else {
                    log.warn("Không tìm thấy gói Student nào với chu kỳ BillingCycle.MONTHLY");
                }
            } else {
                log.info("Tài khoản {} đã có gói Student Monthly", targetEmail);
            }
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