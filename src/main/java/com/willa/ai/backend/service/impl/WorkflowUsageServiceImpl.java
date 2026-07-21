package com.willa.ai.backend.service.impl;

import com.willa.ai.backend.config.AnalyticsExcludedUsersProperties;
import com.willa.ai.backend.dto.response.WorkflowUsageReportResponse;
import com.willa.ai.backend.dto.response.WorkflowUsageReportResponse.UserWorkflowUsageSummary;
import com.willa.ai.backend.dto.response.WorkflowUsageReportResponse.WorkflowUsageLogItem;
import com.willa.ai.backend.dto.response.WorkflowUsageReportResponse.WorkflowUsageSummaryItem;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.WorkflowUsage;
import com.willa.ai.backend.entity.enums.WorkflowType;
import com.willa.ai.backend.entity.enums.WorkflowUsageStatus;
import com.willa.ai.backend.exception.ResourceNotFoundException;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.repository.WorkflowUsageRepository;
import com.willa.ai.backend.service.WorkflowUsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class WorkflowUsageServiceImpl implements WorkflowUsageService {

    private static final int MAX_LOG_ROWS = 500;

    private final WorkflowUsageRepository workflowUsageRepository;
    private final UserRepository userRepository;
    private final WorkflowUsageRecorder workflowUsageRecorder;
    private final AnalyticsExcludedUsersProperties excludedUsers;

    @Override
    public <T> T track(User user, WorkflowType workflow, Long chatSessionId, Supplier<T> action) {
        LocalDateTime startedAt = LocalDateTime.now();
        long startMs = System.currentTimeMillis();
        WorkflowUsageStatus status = WorkflowUsageStatus.SUCCESS;
        String errorMessage = null;
        try {
            return action.get();
        } catch (RuntimeException e) {
            status = WorkflowUsageStatus.FAILED;
            errorMessage = truncate(e.getMessage(), 500);
            throw e;
        } finally {
            workflowUsageRecorder.record(
                    user,
                    workflow,
                    chatSessionId,
                    startedAt,
                    System.currentTimeMillis() - startMs,
                    status,
                    errorMessage);
        }
    }

    @Override
    @Transactional
    public void trackRunnable(User user, WorkflowType workflow, Long chatSessionId, Runnable action) {
        track(user, workflow, chatSessionId, () -> {
            action.run();
            return null;
        });
    }

    @Override
    @Transactional(readOnly = true)
    public WorkflowUsageReportResponse getReportForUser(
            Long userId, LocalDateTime from, LocalDateTime to, boolean includeLogs) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found: " + userId);
        }
        return getReport(List.of(userId), from, to, includeLogs);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkflowUsageReportResponse getReport(
            Collection<Long> userIds,
            LocalDateTime from,
            LocalDateTime to,
            boolean includeLogs) {
        if (userIds == null || userIds.isEmpty()) {
            throw new IllegalArgumentException("At least one userId is required");
        }
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to are required");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to must be after from");
        }

        List<Long> ids = userIds.stream().distinct().toList();

        Object[] totals = unwrapSingletonRow(workflowUsageRepository.totalDurationAndCount(ids, from, to));
        long totalDurationMs = totals[0] != null ? ((Number) totals[0]).longValue() : 0L;
        long totalRuns = totals[1] != null ? ((Number) totals[1]).longValue() : 0L;
        long failedRuns = nullSafeLong(workflowUsageRepository.countFailedInRange(ids, from, to));
        long successfulRuns = Math.max(totalRuns - failedRuns, 0L);

        List<WorkflowUsageSummaryItem> byWorkflow = workflowUsageRepository
                .aggregateByWorkflow(ids, from, to)
                .stream()
                .map(row -> WorkflowUsageSummaryItem.builder()
                        .workflow(row[0].toString())
                        .runCount(((Number) row[1]).longValue())
                        .totalDurationMs(((Number) row[2]).longValue())
                        .avgDurationMs(((Number) row[3]).doubleValue())
                        .build())
                .toList();

        Map<Long, Map<String, Long>> perUserWorkflow = new HashMap<>();
        for (Object[] row : workflowUsageRepository.aggregateByUserAndWorkflow(ids, from, to)) {
            long uid = ((Number) row[0]).longValue();
            String wf = row[1].toString();
            long dur = ((Number) row[3]).longValue();
            perUserWorkflow.computeIfAbsent(uid, k -> new HashMap<>()).put(wf, dur);
        }

        List<UserWorkflowUsageSummary> byUser = workflowUsageRepository
                .aggregateByUser(ids, from, to)
                .stream()
                .map(row -> {
                    long uid = ((Number) row[0]).longValue();
                    return UserWorkflowUsageSummary.builder()
                            .userId(uid)
                            .email((String) row[1])
                            .runCount(((Number) row[2]).longValue())
                            .totalDurationMs(((Number) row[3]).longValue())
                            .durationMsByWorkflow(perUserWorkflow.getOrDefault(uid, Map.of()))
                            .build();
                })
                .toList();

        List<WorkflowUsageLogItem> logs = List.of();
        if (includeLogs) {
            logs = workflowUsageRepository
                    .findByUser_IdInAndStartedAtBetweenOrderByStartedAtDesc(
                            ids, from, to, PageRequest.of(0, MAX_LOG_ROWS))
                    .getContent()
                    .stream()
                    .map(this::toLogItem)
                    .toList();
        }

        return WorkflowUsageReportResponse.builder()
                .from(from)
                .to(to)
                .userIds(ids)
                .totalRuns(totalRuns)
                .totalDurationMs(totalDurationMs)
                .activeUsers((long) ids.size())
                .failedRuns(failedRuns)
                .successfulRuns(successfulRuns)
                .byWorkflow(byWorkflow)
                .byUser(byUser)
                .logs(logs)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public WorkflowUsageReportResponse getSystemReport(
            LocalDateTime from, LocalDateTime to, boolean includeLogs) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to are required");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to must be after from");
        }

        Collection<Long> excluded = excludedUsers.queryIds();
        Object[] totals = unwrapSingletonRow(
                workflowUsageRepository.totalDurationAndCountInRange(from, to, excluded));
        long totalDurationMs = totals[0] != null ? ((Number) totals[0]).longValue() : 0L;
        long totalRuns = totals[1] != null ? ((Number) totals[1]).longValue() : 0L;
        long failedRuns = nullSafeLong(workflowUsageRepository.countFailedInRangeAll(from, to, excluded));
        long successfulRuns = Math.max(totalRuns - failedRuns, 0L);
        Long activeUsers = workflowUsageRepository.countDistinctUsersInRange(from, to, excluded);

        List<WorkflowUsageSummaryItem> byWorkflow = workflowUsageRepository
                .aggregateByWorkflowInRange(from, to, excluded)
                .stream()
                .map(row -> WorkflowUsageSummaryItem.builder()
                        .workflow(row[0].toString())
                        .runCount(((Number) row[1]).longValue())
                        .totalDurationMs(((Number) row[2]).longValue())
                        .avgDurationMs(((Number) row[3]).doubleValue())
                        .build())
                .toList();

        List<UserWorkflowUsageSummary> byUser = workflowUsageRepository
                .usersByWorkflowTimeInRange(from, to, excluded)
                .stream()
                .map(row -> UserWorkflowUsageSummary.builder()
                        .userId(((Number) row[0]).longValue())
                        .email((String) row[1])
                        .runCount(((Number) row[2]).longValue())
                        .totalDurationMs(((Number) row[3]).longValue())
                        .durationMsByWorkflow(Map.of())
                        .build())
                .toList();

        List<WorkflowUsageLogItem> logs = List.of();
        if (includeLogs) {
            logs = workflowUsageRepository
                    .findByStartedAtBetweenOrderByStartedAtDesc(from, to, PageRequest.of(0, MAX_LOG_ROWS))
                    .getContent()
                    .stream()
                    .map(this::toLogItem)
                    .toList();
        }

        return WorkflowUsageReportResponse.builder()
                .from(from)
                .to(to)
                .userIds(List.of())
                .totalRuns(totalRuns)
                .totalDurationMs(totalDurationMs)
                .activeUsers(activeUsers != null ? activeUsers : 0L)
                .failedRuns(failedRuns)
                .successfulRuns(successfulRuns)
                .byWorkflow(byWorkflow)
                .byUser(byUser)
                .logs(logs)
                .build();
    }

    private static Object[] unwrapSingletonRow(Object[] row) {
        if (row != null && row.length == 1 && row[0] instanceof Object[] nested) {
            return nested;
        }
        return row;
    }

    private static long nullSafeLong(Long value) {
        return value != null ? value : 0L;
    }

    private WorkflowUsageLogItem toLogItem(WorkflowUsage w) {
        return WorkflowUsageLogItem.builder()
                .id(w.getId())
                .userId(w.getUser().getId())
                .workflow(w.getWorkflow().name())
                .chatSessionId(w.getChatSessionId())
                .startedAt(w.getStartedAt())
                .endedAt(w.getEndedAt())
                .durationMs(w.getDurationMs())
                .status(w.getStatus().name())
                .build();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
