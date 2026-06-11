package com.willa.ai.backend.service;

import com.willa.ai.backend.entity.Subscription;
import com.willa.ai.backend.entity.Wallet;

/**
 * Quyết toán token cuối chu kỳ gói MONTHLY/YEARLY.
 * <ul>
 *   <li>Chưa xài hết gói: chỉ giữ {@code periodStartTokenBalance} (thu hồi phần token plan còn dư).</li>
 *   <li>Xài lố (số dư &lt; số dư trước khi mua plan): giữ nguyên số dư hiện tại.</li>
 * </ul>
 */
public interface SubscriptionTokenSettlementService {

    /**
     * @return số dư sau quyết toán
     */
    long settleRecurringPeriod(Wallet wallet, Subscription subscription);
}
