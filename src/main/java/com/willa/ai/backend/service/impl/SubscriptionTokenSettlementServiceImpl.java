package com.willa.ai.backend.service.impl;

import org.springframework.stereotype.Service;

import com.willa.ai.backend.entity.Plan;
import com.willa.ai.backend.entity.Subscription;
import com.willa.ai.backend.entity.Wallet;
import com.willa.ai.backend.entity.enums.BillingCycle;
import com.willa.ai.backend.service.SubscriptionTokenSettlementService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class SubscriptionTokenSettlementServiceImpl implements SubscriptionTokenSettlementService {

    @Override
    public long settleRecurringPeriod(Wallet wallet, Subscription subscription) {
        Plan plan = subscription.getPlan();
        if (plan.getBillingCycle() == BillingCycle.ONE_TIME) {
            return wallet.getTokenBalance();
        }

        long grant = subscription.getPeriodTokenGrant() != null
                ? subscription.getPeriodTokenGrant()
                : plan.getTokenLimit().longValue();
        long balanceBeforeGrant = subscription.getPeriodStartTokenBalance() != null
                ? subscription.getPeriodStartTokenBalance()
                : 0L;
        long current = wallet.getTokenBalance();

        long newBalance;
        if (current >= balanceBeforeGrant) {
            // Chưa xài hết token gói → chỉ giữ số dư trước khi được cộng plan
            newBalance = balanceBeforeGrant;
            log.info(
                    "Token settlement (under-use): userId={} subId={} plan={} grant={} balanceBeforeGrant={} before={} after={} forfeited={}",
                    wallet.getUser().getId(),
                    subscription.getId(),
                    plan.getName(),
                    grant,
                    balanceBeforeGrant,
                    current,
                    newBalance,
                    current - newBalance);
        } else {
            // Xài lố plan (xuống dưới số dư lúc mua gói) → giữ nguyên số dư còn lại
            newBalance = current;
            log.info(
                    "Token settlement (over-use): userId={} subId={} plan={} grant={} balanceBeforeGrant={} kept={} overdraw={}",
                    wallet.getUser().getId(),
                    subscription.getId(),
                    plan.getName(),
                    grant,
                    balanceBeforeGrant,
                    newBalance,
                    balanceBeforeGrant - current);
        }

        wallet.setTokenBalance(newBalance);
        return newBalance;
    }
}
