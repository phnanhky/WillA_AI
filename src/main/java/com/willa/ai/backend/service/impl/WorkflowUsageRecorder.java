package com.willa.ai.backend.service.impl;

import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.WorkflowUsage;
import com.willa.ai.backend.entity.enums.WorkflowType;
import com.willa.ai.backend.entity.enums.WorkflowUsageStatus;
import com.willa.ai.backend.repository.WorkflowUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
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
        try {
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
        } catch (Exception e) {
            // Không làm fail request chính (vd. CHECK constraint thiếu enum mới)
            log.warn("Failed to record workflow usage {}: {}", workflow, e.getMessage());
        }
    }
}
