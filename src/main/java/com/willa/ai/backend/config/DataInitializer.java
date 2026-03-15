package com.willa.ai.backend.config;

import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.enums.Role;
import com.willa.ai.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.default.email}")
    private String adminEmail;

    @Value("${admin.default.password}")
    private String adminPassword;

    @Value("${admin.default.name}")
    private String adminName;

    @Override
    public void run(String... args) throws Exception {
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
    }
}