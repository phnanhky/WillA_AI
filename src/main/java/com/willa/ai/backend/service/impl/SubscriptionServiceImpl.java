package com.willa.ai.backend.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.willa.ai.backend.dto.request.SubscriptionRequest;
import com.willa.ai.backend.dto.response.SubscriptionResponse;
import com.willa.ai.backend.entity.Plan;
import com.willa.ai.backend.entity.Subscription;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.Wallet;
import com.willa.ai.backend.entity.enums.BillingCycle;
import com.willa.ai.backend.entity.enums.SubscriptionStatus;
import com.willa.ai.backend.exception.ResourceNotFoundException;
import com.willa.ai.backend.repository.PlanRepository;
import com.willa.ai.backend.repository.SubscriptionRepository;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.repository.WalletRepository;
import com.willa.ai.backend.service.SubscriptionService;

import lombok.RequiredArgsConstructor;

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

        if (plan.getBillingCycle() == BillingCycle.ONE_TIME) {
            creditWalletTokens(user, plan);
            return buildOneTimeTopUpResponse(user, plan);
        }

        cancelActiveRecurringSubscriptions(user.getId());

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

        creditWalletTokens(user, plan);
        clearReviewRequirementIfPaidPlan(user, plan);

        return mapToResponse(savedSubscription);
    }

    private LocalDateTime calculateEndDate(LocalDateTime startDate, BillingCycle cycle) {
        return switch (cycle) {
            case MONTHLY -> startDate.plusMonths(1);
            case YEARLY -> startDate.plusYears(1);
            case ONE_TIME -> startDate.plusYears(100);
        };
    }

    @Override
    @Transactional
    public Page<SubscriptionResponse> getUserSubscriptions(String email, int page, int size) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        Pageable pageable = PageRequest.of(page, size, org.springframework.data.domain.Sort.by("createdAt").descending());
        Page<Subscription> subscriptions = subscriptionRepository.findByUserId(user.getId(), pageable);
        LocalDateTime now = LocalDateTime.now();
        subscriptions.forEach(sub -> checkAndExpireSubscriptionInDb(sub, now));
        return subscriptions.map(this::mapToResponse);
    }

    @Override
    @Transactional
    public Page<SubscriptionResponse> getAllSubscriptions(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, org.springframework.data.domain.Sort.by("createdAt").descending());
        Page<Subscription> subscriptions = subscriptionRepository.findAll(pageable);
        LocalDateTime now = LocalDateTime.now();
        subscriptions.forEach(sub -> checkAndExpireSubscriptionInDb(sub, now));
        return subscriptions.map(this::mapToResponse);
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

        if (subscription.getPlan().getBillingCycle() == BillingCycle.ONE_TIME) {
            throw new IllegalArgumentException("Gói mua thêm token (ONE_TIME) không hủy qua subscription — chỉ cộng token vào ví.");
        }

        subscription.setStatus(SubscriptionStatus.CANCELLED);

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

        if (plan.getName().toLowerCase().contains("student") && !Boolean.TRUE.equals(user.getIsStudent())) {
            throw new IllegalArgumentException("Chỉ tài khoản đã xác thực sinh viên mới được đăng ký gói Student.");
        }

        // ONE_TIME: chỉ nạp token, không đụng subscription tháng/năm đang active.
        if (plan.getBillingCycle() == BillingCycle.ONE_TIME) {
            creditWalletTokens(user, plan);
            return;
        }

        cancelActiveRecurringSubscriptions(user.getId());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = calculateEndDate(now, plan.getBillingCycle());

        Subscription subscription = new Subscription();
        subscription.setUser(user);
        subscription.setPlan(plan);
        subscription.setStartDate(now);
        subscription.setEndDate(endDate);
        subscription.setStatus(SubscriptionStatus.ACTIVE);

        subscriptionRepository.save(subscription);

        creditWalletTokens(user, plan);
        clearReviewRequirementIfPaidPlan(user, plan);
    }

    private void cancelActiveRecurringSubscriptions(Long userId) {
        List<Subscription> activeSubs = subscriptionRepository.findActiveRecurringByUserId(
                userId, SubscriptionStatus.ACTIVE);
        for (Subscription sub : activeSubs) {
            sub.setStatus(SubscriptionStatus.CANCELLED);
            subscriptionRepository.save(sub);
        }
    }

    private void creditWalletTokens(User user, Plan plan) {
        Wallet wallet = walletRepository.findByUserId(user.getId())
                .orElse(Wallet.builder().user(user).tokenBalance(0L).totalRecharged(0L).build());
        long grant = Long.valueOf(plan.getTokenLimit());
        wallet.setTokenBalance(wallet.getTokenBalance() + grant);
        wallet.setTotalRecharged(wallet.getTotalRecharged() + grant);
        walletRepository.save(wallet);
    }

    private void clearReviewRequirementIfPaidPlan(User user, Plan plan) {
        if (!plan.getName().equalsIgnoreCase("Free")) {
            user.setRequiresReview(false);
            userRepository.save(user);
        }
    }

    private SubscriptionResponse buildOneTimeTopUpResponse(User user, Plan plan) {
        LocalDateTime now = LocalDateTime.now();
        return SubscriptionResponse.builder()
                .userId(user.getId())
                .userEmail(user.getEmail())
                .planId(plan.getId())
                .planName(plan.getName())
                .limitGranted(plan.getTokenLimit())
                .startDate(now)
                .endDate(now)
                .status(SubscriptionStatus.ACTIVE)
                .build();
    }

    private SubscriptionResponse mapToResponse(Subscription subscription) {
        SubscriptionStatus currentStatus = subscription.getStatus();
        if (currentStatus == SubscriptionStatus.ACTIVE 
                && subscription.getEndDate() != null 
                && subscription.getEndDate().isBefore(LocalDateTime.now())) {
            currentStatus = SubscriptionStatus.EXPIRED;
        }
        return SubscriptionResponse.builder()
                .id(subscription.getId())
                .userId(subscription.getUser().getId())
                .userEmail(subscription.getUser().getEmail())
                .planId(subscription.getPlan().getId())
                .planName(subscription.getPlan().getName())
                .limitGranted(subscription.getPlan().getTokenLimit())
                .startDate(subscription.getStartDate())
                .endDate(subscription.getEndDate())
                .status(currentStatus)
                .createdAt(subscription.getCreatedAt())
                .build();
    }

    private void checkAndExpireSubscriptionInDb(Subscription sub, LocalDateTime now) {
        if (sub.getStatus() == SubscriptionStatus.ACTIVE 
                && sub.getPlan().getBillingCycle() != BillingCycle.ONE_TIME
                && sub.getEndDate() != null 
                && sub.getEndDate().isBefore(now)) {
            sub.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionRepository.save(sub);

            boolean hasOtherActiveRecurring = subscriptionRepository
                    .findActiveRecurringByUserId(sub.getUser().getId(), SubscriptionStatus.ACTIVE)
                    .stream()
                    .anyMatch(other -> !other.getId().equals(sub.getId()));
            if (!hasOtherActiveRecurring) {
                planRepository.findByName("Free").ifPresent(freePlan -> {
                    Subscription newFreeSub = Subscription.builder()
                            .user(sub.getUser())
                            .plan(freePlan)
                            .startDate(now)
                            .endDate(now.plusYears(100))
                            .status(SubscriptionStatus.ACTIVE)
                            .build();
                    subscriptionRepository.save(newFreeSub);
                });
            }
        }
    }
}
