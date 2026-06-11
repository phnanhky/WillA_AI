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
import com.willa.ai.backend.service.SubscriptionTokenSettlementService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final SubscriptionTokenSettlementService tokenSettlementService;

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

        Wallet wallet = settleAndCancelActiveRecurring(user);

        LocalDateTime startDate = LocalDateTime.now();
        LocalDateTime endDate = calculateEndDate(startDate, plan.getBillingCycle());

        long balanceBeforeGrant = wallet.getTokenBalance();
        long grant = plan.getTokenLimit().longValue();

        Subscription subscription = Subscription.builder()
                .user(user)
                .plan(plan)
                .startDate(startDate)
                .endDate(endDate)
                .status(SubscriptionStatus.ACTIVE)
                .periodStartTokenBalance(balanceBeforeGrant)
                .periodTokenGrant(grant)
                .build();

        Subscription savedSubscription = subscriptionRepository.save(subscription);

        creditRecurringPlanTokens(wallet, grant);
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

        Wallet wallet = getOrCreateWallet(user);
        tokenSettlementService.settleRecurringPeriod(wallet, subscription);
        walletRepository.save(wallet);

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

        Wallet wallet = settleAndCancelActiveRecurring(user);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = calculateEndDate(now, plan.getBillingCycle());

        long balanceBeforeGrant = wallet.getTokenBalance();
        long grant = plan.getTokenLimit().longValue();

        Subscription subscription = new Subscription();
        subscription.setUser(user);
        subscription.setPlan(plan);
        subscription.setStartDate(now);
        subscription.setEndDate(endDate);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setPeriodStartTokenBalance(balanceBeforeGrant);
        subscription.setPeriodTokenGrant(grant);

        subscriptionRepository.save(subscription);

        creditRecurringPlanTokens(wallet, grant);
        clearReviewRequirementIfPaidPlan(user, plan);
    }

    private Wallet getOrCreateWallet(User user) {
        return walletRepository.findByUserId(user.getId())
                .orElseGet(() -> walletRepository.save(
                        Wallet.builder().user(user).tokenBalance(0L).totalRecharged(0L).build()));
    }

    /** Quyết toán token gói cũ, hủy subscription recurring đang active, trả ví sau quyết toán. */
    private Wallet settleAndCancelActiveRecurring(User user) {
        Wallet wallet = getOrCreateWallet(user);
        List<Subscription> activeSubs = subscriptionRepository.findActiveRecurringByUserId(
                user.getId(), SubscriptionStatus.ACTIVE);
        for (Subscription sub : activeSubs) {
            tokenSettlementService.settleRecurringPeriod(wallet, sub);
            sub.setStatus(SubscriptionStatus.CANCELLED);
            subscriptionRepository.save(sub);
            walletRepository.save(wallet);
            log.info("Cancelled recurring subscription {} after token settlement for user {}",
                    sub.getId(), user.getEmail());
        }
        return wallet;
    }

    private void creditWalletTokens(User user, Plan plan) {
        Wallet wallet = getOrCreateWallet(user);
        long grant = plan.getTokenLimit().longValue();
        wallet.setTokenBalance(wallet.getTokenBalance() + grant);
        wallet.setTotalRecharged(wallet.getTotalRecharged() + grant);
        walletRepository.save(wallet);
    }

    private void creditRecurringPlanTokens(Wallet wallet, long grant) {
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
            Wallet wallet = getOrCreateWallet(sub.getUser());
            tokenSettlementService.settleRecurringPeriod(wallet, sub);
            walletRepository.save(wallet);

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
