package com.willa.ai.backend.service.impl;

import com.willa.ai.backend.dto.request.WorkspaceExpertRequest;
import com.willa.ai.backend.dto.response.AdminWorkspaceSummaryResponse;
import com.willa.ai.backend.dto.response.WorkspaceExpertResponse;
import com.willa.ai.backend.entity.Plan;
import com.willa.ai.backend.entity.Subscription;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.Wallet;
import com.willa.ai.backend.entity.Workspace;
import com.willa.ai.backend.entity.WorkspaceExpert;
import com.willa.ai.backend.entity.WorkspacePlan;
import com.willa.ai.backend.entity.enums.Role;
import com.willa.ai.backend.entity.enums.SubscriptionStatus;
import com.willa.ai.backend.entity.enums.WorkspacePlanTier;
import com.willa.ai.backend.repository.PlanRepository;
import com.willa.ai.backend.repository.SubscriptionRepository;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.repository.WalletRepository;
import com.willa.ai.backend.repository.WorkspaceExpertRepository;
import com.willa.ai.backend.repository.WorkspaceRepository;
import com.willa.ai.backend.service.EmailService;
import com.willa.ai.backend.service.ExpertService;
import com.willa.ai.backend.service.WorkspacePlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ExpertServiceImpl implements ExpertService {

    private static final String PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private final WorkspaceExpertRepository expertRepository;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final WalletRepository walletRepository;
    private final WorkspacePlanService workspacePlanService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.frontendUrl:${app.baseUrl}}")
    private String frontendUrl;

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceExpertResponse> listAllExperts() {
        return expertRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(e -> mapToResponse(e, null, null))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceExpertResponse> listActiveExperts() {
        return expertRepository.findByIsActiveTrueOrderByCreatedAtDesc().stream()
                .map(e -> mapToResponse(e, null, null))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceExpertResponse> listPlatformExperts() {
        return listActiveExperts();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceExpertResponse> listWorkspaceExperts(String currentEmail, Long workspaceId) {
        return listActiveExperts();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminWorkspaceSummaryResponse> listAllWorkspacesForAdmin() {
        return workspaceRepository.findAll().stream()
                .sorted(Comparator.comparing(Workspace::getTitle, String.CASE_INSENSITIVE_ORDER))
                .map(w -> AdminWorkspaceSummaryResponse.builder()
                        .id(w.getId())
                        .title(w.getTitle())
                        .ownerName(w.getOwner() != null ? w.getOwner().getFullName() : null)
                        .ownerEmail(w.getOwner() != null ? w.getOwner().getEmail() : null)
                        .build())
                .toList();
    }

    @Override
    public WorkspaceExpertResponse createExpert(WorkspaceExpertRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        boolean createIfMissing = request.getCreateAccountIfMissing() == null
                || Boolean.TRUE.equals(request.getCreateAccountIfMissing());
        boolean sendInvite = request.getSendInviteEmail() == null
                || Boolean.TRUE.equals(request.getSendInviteEmail());

        User user = userRepository.findByEmail(email).orElse(null);
        boolean accountCreated = false;
        String plainPassword = null;

        if (user == null) {
            if (!createIfMissing) {
                throw new RuntimeException("Không tìm thấy người dùng với email: " + email
                        + ". Bật «Tạo tài khoản nếu chưa có» để mời expert mới.");
            }
            String fullName = trimOrNull(request.getFullName());
            if (fullName == null) {
                throw new RuntimeException("Full name is required when creating a new expert account");
            }
            plainPassword = generateTempPassword();
            user = createExpertUser(email, fullName, plainPassword, trimOrNull(request.getAvatarUrl()));
            accountCreated = true;
            log.info("Created User account for new expert email={}", email);
        } else {
            if (expertRepository.existsByUserId(user.getId())) {
                throw new RuntimeException("Người dùng đã là expert trên Willa");
            }
            applyUserProfileUpdates(user, request);
            // Expert cần đăng nhập được
            if (!Boolean.TRUE.equals(user.getIsEnabled())) {
                user.setIsEnabled(true);
                user.setVerificationToken(null);
                userRepository.save(user);
            }
        }

        WorkspaceExpert expert = expertRepository.save(WorkspaceExpert.builder()
                .workspace(null)
                .user(user)
                .expertise(trimOrNull(request.getExpertise()))
                .bio(trimOrNull(request.getBio()))
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .reviewPrice(normalizePrice(request.getReviewPrice()))
                .hourlyRate(normalizePrice(request.getHourlyRate()))
                .build());
        log.info("Admin assigned app-wide expert userId={} accountCreated={}", user.getId(), accountCreated);

        boolean inviteEmailSent = false;
        if (sendInvite) {
            String loginUrl = normalizeFrontendUrl() + "/login";
            try {
                if (accountCreated && plainPassword != null) {
                    emailService.sendExpertInviteEmail(
                            user.getEmail(), user.getFullName(), plainPassword, loginUrl);
                } else {
                    emailService.sendExpertAssignedEmail(
                            user.getEmail(), user.getFullName(), loginUrl);
                }
                inviteEmailSent = true;
            } catch (Exception e) {
                log.error("Failed to send expert invite email to {}: {}", email, e.getMessage());
                // không rollback expert — admin vẫn thấy accountCreated và có thể gửi lại tay
            }
        }

        return mapToResponse(expert, accountCreated, inviteEmailSent);
    }

    @Override
    public WorkspaceExpertResponse updateExpert(Long expertId, WorkspaceExpertRequest request) {
        WorkspaceExpert expert = expertRepository.findById(expertId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy expert"));

        if (request.getExpertise() != null) {
            expert.setExpertise(trimOrNull(request.getExpertise()));
        }
        if (request.getBio() != null) {
            expert.setBio(trimOrNull(request.getBio()));
        }
        if (request.getIsActive() != null) {
            expert.setIsActive(request.getIsActive());
        }
        if (request.getReviewPrice() != null) {
            expert.setReviewPrice(normalizePrice(request.getReviewPrice()));
        }
        if (request.getHourlyRate() != null) {
            expert.setHourlyRate(normalizePrice(request.getHourlyRate()));
        }
        applyUserProfileUpdates(expert.getUser(), request);

        return mapToResponse(expertRepository.save(expert), null, null);
    }

    @Override
    public void deleteExpert(Long expertId) {
        if (!expertRepository.existsById(expertId)) {
            throw new RuntimeException("Không tìm thấy expert");
        }
        expertRepository.deleteById(expertId);
    }

    private User createExpertUser(String email, String fullName, String plainPassword, String avatarUrl) {
        User user = User.builder()
                .email(email)
                .fullName(fullName)
                .password(passwordEncoder.encode(plainPassword))
                .avatarUrl(avatarUrl)
                .role(Role.USER)
                .isEnabled(true)
                .isActive(true)
                .isStudent(false)
                .requiresReview(false)
                .workspacePlanTier(WorkspacePlanTier.FREE_WORKSPACE)
                .build();
        user = userRepository.save(user);
        assignDefaultWorkspacePlan(user);
        seedFreeFeedbackEntitlements(user);
        return user;
    }

    private void applyUserProfileUpdates(User user, WorkspaceExpertRequest request) {
        boolean dirty = false;
        String fullName = trimOrNull(request.getFullName());
        if (fullName != null && !fullName.equals(user.getFullName())) {
            user.setFullName(fullName);
            dirty = true;
        }
        String avatarUrl = trimOrNull(request.getAvatarUrl());
        if (avatarUrl != null) {
            user.setAvatarUrl(avatarUrl);
            dirty = true;
        }
        if (dirty) {
            userRepository.save(user);
        }
    }

    private void assignDefaultWorkspacePlan(User user) {
        try {
            WorkspacePlan plan = workspacePlanService.getDefaultPlan();
            user.setWorkspacePlan(plan);
            try {
                user.setWorkspacePlanTier(WorkspacePlanTier.valueOf(plan.getCode()));
            } catch (IllegalArgumentException ignored) {
                user.setWorkspacePlanTier(WorkspacePlanTier.FREE_WORKSPACE);
            }
            userRepository.save(user);
        } catch (Exception ignored) {
            // workspace_plans chưa migrate
        }
    }

    private void seedFreeFeedbackEntitlements(User user) {
        Plan freePlan = planRepository.findByName("Free").orElse(null);
        long tokens = freePlan != null ? freePlan.getTokenLimit().longValue() : 10_000L;
        if (freePlan != null) {
            subscriptionRepository.save(Subscription.builder()
                    .user(user)
                    .plan(freePlan)
                    .startDate(LocalDateTime.now())
                    .endDate(LocalDateTime.now().plusYears(100))
                    .status(SubscriptionStatus.ACTIVE)
                    .build());
        }
        walletRepository.save(Wallet.builder()
                .user(user)
                .tokenBalance(tokens)
                .totalRecharged(tokens)
                .build());
    }

    private String generateTempPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder("Willa!");
        for (int i = 0; i < 10; i++) {
            sb.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    private String normalizeFrontendUrl() {
        String base = frontendUrl != null ? frontendUrl.trim() : "https://willaai.tech";
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private String trimOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Long normalizePrice(Long value) {
        return value != null && value > 0 ? value : null;
    }

    private WorkspaceExpertResponse mapToResponse(
            WorkspaceExpert expert, Boolean accountCreated, Boolean inviteEmailSent) {
        User user = expert.getUser();
        return WorkspaceExpertResponse.builder()
                .id(expert.getId())
                .userId(user.getId())
                .userEmail(user.getEmail())
                .userFullName(user.getFullName())
                .userAvatarUrl(user.getAvatarUrl())
                .workspaceId(null)
                .workspaceTitle(null)
                .platformExpert(true)
                .expertise(expert.getExpertise())
                .bio(expert.getBio())
                .isActive(expert.getIsActive())
                .reviewPrice(expert.getReviewPrice())
                .hourlyRate(expert.getHourlyRate())
                .createdAt(expert.getCreatedAt())
                .accountCreated(accountCreated)
                .inviteEmailSent(inviteEmailSent)
                .build();
    }
}
