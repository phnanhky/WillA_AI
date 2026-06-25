package com.willa.ai.backend.service;

import com.willa.ai.backend.dto.response.WorkspaceSubscriptionResponse;
import org.springframework.data.domain.Page;

public interface WorkspaceSubscriptionService {

    Page<WorkspaceSubscriptionResponse> getUserSubscriptions(String email, int page, int size);

    Page<WorkspaceSubscriptionResponse> getAllSubscriptions(int page, int size);

    WorkspaceSubscriptionResponse cancelSubscription(String email, Long subscriptionId);

    void createOrUpdateSubscription(String email, Long workspacePlanId);

    void assignDefaultFreeSubscription(String email);
}
