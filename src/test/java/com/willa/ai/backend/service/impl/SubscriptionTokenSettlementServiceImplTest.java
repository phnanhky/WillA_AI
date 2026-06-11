package com.willa.ai.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.willa.ai.backend.entity.Plan;
import com.willa.ai.backend.entity.Subscription;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.Wallet;
import com.willa.ai.backend.entity.enums.BillingCycle;

class SubscriptionTokenSettlementServiceImplTest {

    private SubscriptionTokenSettlementServiceImpl service;
    private User user;
    private Plan plan;
    private Wallet wallet;
    private Subscription subscription;

    @BeforeEach
    void setUp() {
        service = new SubscriptionTokenSettlementServiceImpl();
        user = User.builder().id(1L).email("u@test.com").build();
        plan = Plan.builder().name("Pro").billingCycle(BillingCycle.MONTHLY).tokenLimit(1_000).build();
        wallet = Wallet.builder().user(user).tokenBalance(0L).totalRecharged(0L).build();
        subscription = Subscription.builder()
                .id(10L)
                .user(user)
                .plan(plan)
                .periodStartTokenBalance(200L)
                .periodTokenGrant(1_000L)
                .build();
    }

    @Test
    void underUse_keepsBalanceBeforeGrant() {
        wallet.setTokenBalance(700L); // 200 + 1000 - 500 used
        long after = service.settleRecurringPeriod(wallet, subscription);
        assertEquals(200L, after);
        assertEquals(200L, wallet.getTokenBalance());
    }

    @Test
    void overUse_keepsRemainingBalance() {
        wallet.setTokenBalance(50L); // below 200 before grant
        long after = service.settleRecurringPeriod(wallet, subscription);
        assertEquals(50L, after);
        assertEquals(50L, wallet.getTokenBalance());
    }

    @Test
    void exactUse_keepsBalanceBeforeGrant() {
        wallet.setTokenBalance(200L);
        long after = service.settleRecurringPeriod(wallet, subscription);
        assertEquals(200L, after);
    }
}
