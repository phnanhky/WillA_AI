package com.willa.ai.backend.service.impl;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.willa.ai.backend.dto.request.*;
import com.willa.ai.backend.dto.response.AuthResponse;
import com.willa.ai.backend.dto.response.TokenResponse;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.enums.Role;
import com.willa.ai.backend.entity.Plan;
import com.willa.ai.backend.entity.Subscription;
import com.willa.ai.backend.entity.Wallet;
import com.willa.ai.backend.entity.enums.SubscriptionStatus;
import com.willa.ai.backend.repository.PlanRepository;
import com.willa.ai.backend.repository.SubscriptionRepository;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.repository.WalletRepository;
import com.willa.ai.backend.security.JwtTokenProvider;
import com.willa.ai.backend.service.AuthenticationService;
import com.willa.ai.backend.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;

@Service
@Transactional
public class AuthenticationServiceImpl implements AuthenticationService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    @Value("${app.mail.from}")
    private String mailFrom;

    @Value("${app.baseUrl}")
    private String baseUrl;
    
    @Value("${app.frontendUrl:http://localhost:3000}")
    private String frontendUrl;

    @Override
    public AuthResponse register(RegisterRequest request) {
        try {
            System.out.println("Register: email=" + request.getEmail());
            
            if (userRepository.findByEmail(request.getEmail()).isPresent()) {
                throw new RuntimeException("User already exists");
            }

            if (!request.getPassword().equals(request.getConfirmPassword())) {
                throw new RuntimeException("Passwords do not match");
            }

            User user = User.builder()
                    .email(request.getEmail())
                    .fullName(request.getFullName())
                    .phoneNumber(request.getPhoneNumber())
                    .password(passwordEncoder.encode(request.getPassword()))
                    .role(Role.USER)
                    .isEnabled(false)
                    .isActive(true)
                    .verificationToken(UUID.randomUUID().toString())
                    .build();

            user = userRepository.save(user);
            System.out.println("User saved: " + user.getId());

            String accessToken = jwtTokenProvider.generateAccessToken(user.getEmail(), user.getId());
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail(), user.getId());

            user.setRefreshToken(refreshToken);
            userRepository.save(user);

            // Assign Free Plan and initialize Wallet
            Optional<Plan> defaultPlanOpt = planRepository.findByName("Free");
            if (defaultPlanOpt.isPresent()) {
                Plan freePlan = defaultPlanOpt.get();
                Subscription freeSub = Subscription.builder()
                        .user(user)
                        .plan(freePlan)
                        .startDate(LocalDateTime.now())
                        .endDate(LocalDateTime.now().plusMonths(1))
                        .status(SubscriptionStatus.ACTIVE)
                        .build();
                subscriptionRepository.save(freeSub);

                Wallet wallet = Wallet.builder()
                        .user(user)
                        .tokenBalance(Long.valueOf(freePlan.getTokenLimit()))
                        .totalRecharged(Long.valueOf(freePlan.getTokenLimit()))
                        .build();
                walletRepository.save(wallet);
            } else {
                // Fallback if Free plan doesn't exist
                Wallet wallet = Wallet.builder()
                        .user(user)
                        .tokenBalance(60000L)
                        .totalRecharged(60000L)
                        .build();
                walletRepository.save(wallet);
            }

            // Send verification email
            String verificationLink = frontendUrl + "/verify-email?token=" + user.getVerificationToken();
            try {
                emailService.sendVerificationEmail(user.getEmail(), verificationLink);
                System.out.println("Verification email sent to: " + user.getEmail());
            } catch (Exception emailEx) {
                System.out.println("Email sending failed: " + emailEx.getMessage());
            }

            // Send welcome email
            try {
                emailService.sendWelcomeEmail(user.getEmail(), user.getFullName());
                System.out.println("Welcome email sent to: " + user.getEmail());
            } catch (Exception emailEx) {
                System.out.println("Welcome email sending failed: " + emailEx.getMessage());
            }

            return AuthResponse.builder()
                    .userId(user.getId())
                    .email(user.getEmail())
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .build();

        } catch (Exception e) {
            System.out.println("Register error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Registration failed: " + e.getMessage());
        }
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        try {
            Optional<User> userOpt = userRepository.findByEmail(request.getEmail());
            if (userOpt.isEmpty()) {
                throw new RuntimeException("Invalid email or password");
            }

            User user = userOpt.get();

            if (!user.getIsEnabled() && !user.getIsActive()) {
                throw new RuntimeException("User account is disabled");
            }

            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                throw new RuntimeException("Invalid email or password");
            }

            String accessToken = jwtTokenProvider.generateAccessToken(user.getEmail(), user.getId());
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail(), user.getId());

            user.setRefreshToken(refreshToken);
            userRepository.save(user);

            return AuthResponse.builder()
                    .userId(user.getId())
                    .email(user.getEmail())
                    .fullName(user.getFullName())
                    .role(user.getRole() != null ? user.getRole().name() : null)
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Login failed: " + e.getMessage());
        }
    }

    @Override
    public TokenResponse refreshToken(RefreshTokenRequest request) {
        try {
            if (!jwtTokenProvider.validateToken(request.getRefreshToken())) {
                throw new RuntimeException("Invalid or expired refresh token");
            }

            String email = jwtTokenProvider.getEmailFromToken(request.getRefreshToken());

            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                throw new RuntimeException("User not found");
            }

            User user = userOpt.get();
            String newAccessToken = jwtTokenProvider.generateAccessToken(email, user.getId());

            return TokenResponse.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(request.getRefreshToken())
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Token refresh failed: " + e.getMessage());
        }
    }

    @Override
    public AuthResponse googleLogin(GoogleLoginRequest request) {
        try {
            var decodedToken = FirebaseAuth.getInstance().verifyIdToken(request.getIdToken());
            String email = decodedToken.getEmail();
            String firebaseUid = decodedToken.getUid();

            Optional<User> userOpt = userRepository.findByEmail(email);
            User user;
            boolean isNewUser = false;

            if (userOpt.isPresent()) {
                user = userOpt.get();
                if (user.getFirebaseUid() == null) {
                    user.setFirebaseUid(firebaseUid);
                }
            } else {
                isNewUser = true;
                Object fullNameObj = decodedToken.getClaims().getOrDefault("name", email);
                String fullName = fullNameObj instanceof String ? (String) fullNameObj : email;
                user = User.builder()
                        .email(email)
                        .fullName(fullName != null ? fullName : email)
                        .firebaseUid(firebaseUid)
                        .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                        .role(Role.USER)
                        .isEnabled(true)
                        .isActive(true)
                        .build();
                user = userRepository.save(user);

                // Assign Free Plan and initialize Wallet
                Optional<Plan> defaultPlanOpt = planRepository.findByName("Free");
                if (defaultPlanOpt.isPresent()) {
                    Plan freePlan = defaultPlanOpt.get();
                    Subscription freeSub = Subscription.builder()
                            .user(user)
                            .plan(freePlan)
                            .startDate(LocalDateTime.now())
                            .endDate(LocalDateTime.now().plusMonths(1))
                            .status(SubscriptionStatus.ACTIVE)
                            .build();
                    subscriptionRepository.save(freeSub);

                    Wallet wallet = Wallet.builder()
                            .user(user)
                            .tokenBalance(Long.valueOf(freePlan.getTokenLimit()))
                            .totalRecharged(Long.valueOf(freePlan.getTokenLimit()))
                            .build();
                    walletRepository.save(wallet);
                } else {
                    Wallet wallet = Wallet.builder()
                            .user(user)
                            .tokenBalance(60000L)
                            .totalRecharged(60000L)
                            .build();
                    walletRepository.save(wallet);
                }
            }

            String accessToken = jwtTokenProvider.generateAccessToken(user.getEmail(), user.getId());
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail(), user.getId());

            user.setRefreshToken(refreshToken);
            userRepository.save(user);

            // Send welcome email for new users
            if (isNewUser) {
                try {
                    emailService.sendWelcomeEmail(user.getEmail(), user.getFullName());
                    System.out.println("Welcome email sent to: " + user.getEmail());
                } catch (Exception emailEx) {
                    System.out.println("Welcome email sending failed: " + emailEx.getMessage());
                }
            }

            return AuthResponse.builder()
                    .userId(user.getId())
                    .email(user.getEmail())
                    .fullName(user.getFullName())
                    .role(user.getRole() != null ? user.getRole().name() : null)
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .build();

        } catch (FirebaseAuthException e) {
            throw new RuntimeException("Firebase authentication failed");
        } catch (Exception e) {
            throw new RuntimeException("Google login failed");
        }
    }

    @Override
    public void logout(String token) {
        // Validate token
        if (!jwtTokenProvider.validateToken(token)) {
            throw new RuntimeException("Invalid or expired token");
        }

        // Get email from token
        String email = jwtTokenProvider.getEmailFromToken(token);
        if (email == null) {
            throw new RuntimeException("Invalid or expired token");
        }

        try {
            // Clear refresh token
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                user.setRefreshToken(null);
                userRepository.save(user);
            }
        } catch (Exception e) {
            throw new RuntimeException("Logout failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        try {
            Optional<User> userOpt = userRepository.findByEmail(request.getEmail());
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                String resetToken = UUID.randomUUID().toString();
                user.setResetToken(resetToken);
                userRepository.save(user);

                // Send password reset email
                String resetLink = frontendUrl + "/reset-password?token=" + resetToken;
                try {
                    emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
                    System.out.println("Password reset email sent to: " + user.getEmail());
                } catch (Exception emailEx) {
                    System.out.println("Email sending failed: " + emailEx.getMessage());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Forgot password failed: " + e.getMessage());
        }
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        try {
            Optional<User> userOpt = userRepository.findByResetToken(request.getToken());
            if (userOpt.isEmpty()) {
                throw new RuntimeException("Invalid or expired reset token");
            }

            User user = userOpt.get();
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
            user.setResetToken(null);
            userRepository.save(user);

            System.out.println("Password reset successfully for: " + user.getEmail());
        } catch (Exception e) {
            throw new RuntimeException("Reset password failed: " + e.getMessage());
        }
    }

    @Override
    public void verifyEmail(String email, String token) {
        try {
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                if (user.getVerificationToken() != null && user.getVerificationToken().equals(token)) {
                    user.setVerificationToken(null);
                    userRepository.save(user);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Email verification failed");
        }
    }
}
