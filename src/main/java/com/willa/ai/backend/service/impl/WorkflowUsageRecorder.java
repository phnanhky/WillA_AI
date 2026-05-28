package com.willa.ai.backend.service.impl;

import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.WorkflowUsage;
import com.willa.ai.backend.entity.enums.WorkflowType;
import com.willa.ai.backend.entity.enums.WorkflowUsageStatus;
import com.willa.ai.backend.repository.WorkflowUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
public class WorkflowUsageRecorder {

    private final WorkflowUsageRepository workflowUsageRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            User user,
            WorkflowType workflow,
            Long chatSessionId,
            LocalDateTime startedAt,
            long durationMs,
            WorkflowUsageStatus status,
            String errorMessage) {
        LocalDateTime endedAt = startedAt.plus(durationMs, ChronoUnit.MILLIS);
        workflowUsageRepository.save(WorkflowUsage.builder()
                .user(user)
                .workflow(workflow)
                .chatSessionId(chatSessionId)
                .startedAt(startedAt)
                .endedAt(endedAt)
                .durationMs(Math.max(durationMs, 0L))
                .status(status)
                .errorMessage(errorMessage)
                .build());
    }
}
