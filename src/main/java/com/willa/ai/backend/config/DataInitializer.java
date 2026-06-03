package com.willa.ai.backend.config;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.service.WalletService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Startup one-off — xóa class sau khi đã cộng token.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private static final List<String> EMAILS = List.of(
            "phnanhky@gmail.com",
            "vuongnmse172460@fpt.edu.vn");
    private static final long TOKENS = 1_000_000L;

    private final UserRepository userRepository;
    private final WalletService walletService;

    @Override
    @Transactional
    public void run(String... args) {
        for (String email : EMAILS) {
            userRepository.findByEmail(email).ifPresentOrElse(
                    user -> {
                        var wallet = walletService.addTokens(user.getId(), TOKENS);
                        log.info("Credited {} tokens to {} — balance={}", TOKENS, email, wallet.getTokenBalance());
                    },
                    () -> log.warn("User {} not found — skip token credit", email));
        }
    }
}
