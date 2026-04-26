package com.willa.ai.backend.service.impl;

import com.willa.ai.backend.dto.request.SubscriptionRequest;
import com.willa.ai.backend.dto.response.SubscriptionResponse;
import com.willa.ai.backend.entity.Plan;
import com.willa.ai.backend.entity.Subscription;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.Wallet;
import com.willa.ai.backend.entity.enums.SubscriptionStatus;
import com.willa.ai.backend.exception.ResourceNotFoundException;
import com.willa.ai.backend.repository.PlanRepository;
import com.willa.ai.backend.repository.SubscriptionRepository;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.repository.WalletRepository;
import com.willa.ai.backend.service.SubscriptionService;
import com.willa.ai.backend.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    @Override
    @Transactional
    public SubscriptionResponse subscribe(String email, SubscriptionRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        Plan plan = planRepository.findById(request.getPlanId())
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));

        if (!plan.getIsActive()) {
            throw new IllegalArgumentException("Cannot subscribe to an inactive plan");
        }

        if (plan.getName().toLowerCase().contains("student") && !Boolean.TRUE.equals(user.getIsStudent())) {
            throw new IllegalArgumentException("Chỉ tài khoản đã xác thực sinh viên mới được đăng ký gói Student.");
        }
        // --- UPGRADE HANDLING ---
        // Find existing active subscriptions for this user and mark them as cancelled/expired
        // because the new plan replaces the old one. We could also carry over the remaining days, 
        // but typically an upgrade starts a fresh cycle.
        List<Subscription> activeSubs = subscriptionRepository.findByUserIdAndStatus(user.getId(), SubscriptionStatus.ACTIVE);
        for (Subscription sub : activeSubs) {
            sub.setStatus(SubscriptionStatus.CANCELLED);
            subscriptionRepository.save(sub);
        }

        // Optionally, reset wallet balance instead of adding if upgrading. 
        // Here we just add tokens, but for explicit tiering we might reset to the new limit
        // if substituting an active plan. Let's assume the new plan replaces the limits.

        LocalDateTime startDate = LocalDateTime.now();
        LocalDateTime endDate = calculateEndDate(startDate, plan.getBillingCycle());

        Subscription subscription = Subscription.builder()
                .user(user)
                .plan(plan)
                .startDate(startDate)
                .endDate(endDate)
                .status(SubscriptionStatus.ACTIVE)
                .build();

        Subscription savedSubscription = subscriptionRepository.save(subscription);

        Wallet wallet = walletRepository.findByUserId(user.getId())
                .orElse(Wallet.builder().user(user).tokenBalance(0L).totalRecharged(0L).build());
        wallet.setTokenBalance(wallet.getTokenBalance() + Long.valueOf(plan.getTokenLimit()));
        wallet.setTotalRecharged(wallet.getTotalRecharged() + plan.getTokenLimit());
        walletRepository.save(wallet);

        return mapToResponse(savedSubscription);
    }

    private LocalDateTime calculateEndDate(LocalDateTime startDate, com.willa.ai.backend.entity.enums.BillingCycle cycle) {
        return switch (cycle) {
            case MONTHLY -> startDate.plusMonths(1);
            case YEARLY -> startDate.plusYears(1);
            case ONE_TIME -> startDate.plusYears(100); // Mua vĩnh viễn hoặc trọn đời
        };
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SubscriptionResponse> getUserSubscriptions(String email, int page, int size) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        Pageable pageable = PageRequest.of(page, size, org.springframework.data.domain.Sort.by("createdAt").descending());
        return subscriptionRepository.findByUserId(user.getId(), pageable).map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SubscriptionResponse> getAllSubscriptions(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, org.springframework.data.domain.Sort.by("createdAt").descending());
        return subscriptionRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Override
    @Transactional
    public SubscriptionResponse cancelSubscription(String email, Long subscriptionId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));

        if (!subscription.getUser().getId().equals(user.getId()) && !user.getRole().name().equals("ADMIN")) {
            throw new IllegalArgumentException("Not authorized to cancel this subscription");
        }

        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new IllegalArgumentException("Only active subscriptions can be cancelled");
        }

        subscription.setStatus(SubscriptionStatus.CANCELLED);
        // Có thể tính toán hoàn lại token nếu cần, nhưng tạm thời giữ nguyên ví.

        Subscription updatedSubscription = subscriptionRepository.save(subscription);
        return mapToResponse(updatedSubscription);
    }

    @Override
    @Transactional
    public void createOrUpdateSubscription(String email, Long planId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found with id: " + planId));

        // Disable existing active subscriptions
        List<Subscription> activeSubs = subscriptionRepository.findByUserIdAndStatus(user.getId(), SubscriptionStatus.ACTIVE);
        for (Subscription sub : activeSubs) {
            sub.setStatus(SubscriptionStatus.CANCELLED);
            subscriptionRepository.save(sub);
        }

        if (plan.getName().toLowerCase().contains("student") && !Boolean.TRUE.equals(user.getIsStudent())) {
            throw new IllegalArgumentException("Chỉ tài khoản đã xác thực sinh viên mới được đăng ký gói Student.");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = plan.getBillingCycle().name().equals("MONTHLY") ? now.plusMonths(1) : now.plusYears(1);

        Subscription subscription = new Subscription();
        subscription.setUser(user);
        subscription.setPlan(plan);
        subscription.setStartDate(now);
        subscription.setEndDate(endDate);
        subscription.setStatus(SubscriptionStatus.ACTIVE);

        subscriptionRepository.save(subscription);

        // Reset Wallet to the new plan tokens
        Wallet wallet = walletRepository.findByUserId(user.getId())
                .orElse(Wallet.builder().user(user).tokenBalance(0L).totalRecharged(0L).build());
        wallet.setTokenBalance(wallet.getTokenBalance() + Long.valueOf(plan.getTokenLimit()));
        wallet.setTotalRecharged(wallet.getTotalRecharged() + plan.getTokenLimit());
        walletRepository.save(wallet);

        // Nâng cấp lên gói trả phí -> Tắt yêu cầu đánh giá tự động block Chat
        if (!plan.getName().equalsIgnoreCase("Free")) {
            user.setRequiresReview(false);
            userRepository.save(user);
        }
    }

    private SubscriptionResponse mapToResponse(Subscription subscription) {
        return SubscriptionResponse.builder()
                .id(subscription.getId())
                .userId(subscription.getUser().getId())
                .userEmail(subscription.getUser().getEmail())
                .planId(subscription.getPlan().getId())
                .planName(subscription.getPlan().getName())
                .limitGranted(subscription.getPlan().getTokenLimit())
                .startDate(subscription.getStartDate())
                .endDate(subscription.getEndDate())
                .status(subscription.getStatus())
                .createdAt(subscription.getCreatedAt())
                .build();
    }
}
