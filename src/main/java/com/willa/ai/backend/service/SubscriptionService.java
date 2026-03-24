package com.willa.ai.backend.service;

import com.willa.ai.backend.dto.request.SubscriptionRequest;
import com.willa.ai.backend.dto.response.SubscriptionResponse;
import org.springframework.data.domain.Page;

public interface SubscriptionService {
    SubscriptionResponse subscribe(String email, SubscriptionRequest request);
    Page<SubscriptionResponse> getUserSubscriptions(String email, int page, int size);
    Page<SubscriptionResponse> getAllSubscriptions(int page, int size);
    SubscriptionResponse cancelSubscription(String email, Long subscriptionId);
    void createOrUpdateSubscription(String email, Long planId);
}
