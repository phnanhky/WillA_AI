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
        updateGenderForUsers();
    }

    private void updateGenderForUsers() {
        log.info("Starting gender data migration...");
        List<User> allUsers = userRepository.findAll();
        
        // Parse list of male IDs
        List<Long> maleIds = List.of(
            5L, 4L, 10L, 11L, 13L, 2L, 16L, 19L, 18L, 6L, 20L, 1L, 26L, 33L, 23L, 35L, 45L, 39L, 41L, 42L, 44L, 50L,
            51L, 55L, 57L, 58L, 230L, 60L, 61L, 63L, 66L, 67L, 68L, 69L, 71L, 76L, 78L, 80L, 82L, 84L, 86L, 89L, 90L,
            100L, 91L, 92L, 93L, 101L, 98L, 96L, 102L, 103L, 104L, 106L, 110L, 111L, 118L, 113L, 115L, 246L, 119L,
            122L, 124L, 125L, 135L, 127L, 128L, 129L, 130L, 131L, 3L, 117L, 247L, 132L, 133L, 138L, 139L, 140L, 141L,
            142L, 143L, 149L, 144L, 152L, 153L, 150L, 136L, 155L, 156L, 157L, 158L, 160L, 161L, 162L, 24L, 163L, 164L,
            165L, 168L, 169L, 171L, 174L, 176L, 178L, 181L, 182L, 183L, 37L, 185L, 179L, 187L, 188L, 193L, 195L, 196L,
            199L, 200L, 202L, 203L, 206L, 208L, 17L, 210L, 211L, 212L, 213L, 216L, 217L, 220L, 218L, 219L, 223L, 224L,
            215L, 14L, 236L, 241L, 242L, 126L, 237L, 233L, 109L, 226L, 239L, 244L, 43L, 229L, 235L
        );
        
        for (User user : allUsers) {
            if (maleIds.contains(user.getId())) {
                user.setGender(Gender.NAM);
            } else {
                user.setGender(Gender.NU);
            }
        }
        
        userRepository.saveAll(allUsers);
        log.info("Finished gender data migration. Updated {} users.", allUsers.size());
    }
}
