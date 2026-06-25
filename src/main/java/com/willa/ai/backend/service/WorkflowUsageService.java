package com.willa.ai.backend.service;

import com.willa.ai.backend.dto.response.WorkflowUsageReportResponse;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.enums.WorkflowType;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public interface WorkflowUsageService {

    /**
     * Run an action and record wall-clock duration for the workflow.
     */
    <T> T track(User user, WorkflowType workflow, Long chatSessionId, Supplier<T> action);

    void trackRunnable(User user, WorkflowType workflow, Long chatSessionId, Runnable action);

    /**
     * Usage report for one or many users in a time range [from, to].
     *
     * @param userIds   one or more user ids (must not be empty)
     * @param from      inclusive start
     * @param to        inclusive end
     * @param includeLogs whether to include individual log rows (max 500)
     */
    WorkflowUsageReportResponse getReport(
            Collection<Long> userIds,
            LocalDateTime from,
            LocalDateTime to,
            boolean includeLogs);

    WorkflowUsageReportResponse getReportForUser(Long userId, LocalDateTime from, LocalDateTime to, boolean includeLogs);

    /** Báo cáo toàn hệ thống (mọi user) trong khoảng thời gian. */
    WorkflowUsageReportResponse getSystemReport(LocalDateTime from, LocalDateTime to, boolean includeLogs);
}
