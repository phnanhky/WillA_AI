package com.willa.ai.backend.service.impl;

import com.willa.ai.backend.dto.response.AnalyticsResponse;
import com.willa.ai.backend.dto.response.AnalyticsResponse.DailyWorkflowStats;
import com.willa.ai.backend.dto.response.AnalyticsResponse.UserActivityDTO;
import com.willa.ai.backend.dto.response.AnalyticsResponse.WorkflowToolStats;
import com.willa.ai.backend.dto.response.AnalyticsResponse.WorkflowTypeStats;
import com.willa.ai.backend.dto.response.AnalyticsResponse.WorkflowUsageAnalytics;
import com.willa.ai.backend.dto.response.AnalyticsResponse.WorkflowUserActivity;
import com.willa.ai.backend.entity.enums.WorkflowType;
import com.willa.ai.backend.repository.AnalyticsRepository;
import com.willa.ai.backend.repository.WorkflowUsageRepository;
import com.willa.ai.backend.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsServiceImpl implements AnalyticsService {
    
    private final AnalyticsRepository analyticsRepository;
    private final WorkflowUsageRepository workflowUsageRepository;
    
    @Override
    public AnalyticsResponse getAnalytics(LocalDate startDate) {
        LocalDateTime startDt = startDate.atStartOfDay();
        LocalDateTime endDt = LocalDateTime.now();
        
        return buildAnalyticsResponse(startDt, endDt);
    }
    
    @Override
    public AnalyticsResponse getTodayAnalytics() {
        LocalDateTime startDt = LocalDate.now().atStartOfDay();
        LocalDateTime endDt = LocalDateTime.now();
        
        return buildAnalyticsResponse(startDt, endDt);
    }
    
    @Override
    public AnalyticsResponse getThisWeekAnalytics() {
        LocalDateTime startDt = LocalDate.now().minusDays(7).atStartOfDay();
        LocalDateTime endDt = LocalDateTime.now();
        
        return buildAnalyticsResponse(startDt, endDt);
    }
    
    private AnalyticsResponse buildAnalyticsResponse(LocalDateTime startDt, LocalDateTime endDt) {
        // Lấy dữ liệu từ repository
        Long totalActiveUsers = analyticsRepository.getActiveUserCount(startDt);
        Long totalChatsToday = analyticsRepository.getChatCount(
            LocalDate.now().atStartOfDay(),
            LocalDateTime.now()
        );
        Long totalChatsThisWeek = analyticsRepository.getChatCount(
            LocalDate.now().minusDays(7).atStartOfDay(),
            LocalDateTime.now()
        );
        Long totalChatsThisMonth = analyticsRepository.getChatCount(
            LocalDate.now().minusDays(30).atStartOfDay(),
            LocalDateTime.now()
        );
        
        // Daily chat counts
        Map<LocalDate, Long> dailyChatCounts = getDailyChatCounts(startDt, endDt);
        
        // Top active users
        List<UserActivityDTO> topActiveUsers = getTopActiveUsers(startDt, 10);
        
        // Feature usage
        Map<String, Long> featureUsageByActionType = getFeatureUsage(startDt);
        
        // Users by plan
        Map<String, Long> usersByPlan = getUsersByPlan();
        
        // Chats by plan
        Map<String, Long> chatsByPlan = getChatsByPlan(startDt);

        WorkflowUsageAnalytics workflowUsage = buildWorkflowUsageAnalytics(startDt, endDt);
        featureUsageByActionType = enrichFeatureUsageWithWorkflows(featureUsageByActionType, workflowUsage);
        
        return AnalyticsResponse.builder()
            .totalActiveUsers(totalActiveUsers != null ? totalActiveUsers : 0)
            .totalChatsToday(totalChatsToday != null ? totalChatsToday : 0)
            .totalChatsThisWeek(totalChatsThisWeek != null ? totalChatsThisWeek : 0)
            .totalChatsThisMonth(totalChatsThisMonth != null ? totalChatsThisMonth : 0)
            .dailyChatCounts(dailyChatCounts)
            .topActiveUsers(topActiveUsers)
            .featureUsageByActionType(featureUsageByActionType)
            .usersByPlan(usersByPlan)
            .chatsByPlan(chatsByPlan)
            .workflowUsage(workflowUsage)
            .build();
    }

    private WorkflowUsageAnalytics buildWorkflowUsageAnalytics(LocalDateTime startDt, LocalDateTime endDt) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime weekStart = LocalDate.now().minusDays(7).atStartOfDay();
        LocalDateTime monthStart = LocalDate.now().minusDays(30).atStartOfDay();

        Object[] periodTotals = workflowUsageRepository.totalDurationAndCountInRange(startDt, endDt);
        long totalDurationMs = periodTotals[0] != null ? ((Number) periodTotals[0]).longValue() : 0L;
        long totalRuns = periodTotals[1] != null ? ((Number) periodTotals[1]).longValue() : 0L;

        Long activeUsers = workflowUsageRepository.countDistinctUsersInRange(startDt, endDt);

        PeriodSnapshot today = periodSnapshot(todayStart, now);
        PeriodSnapshot week = periodSnapshot(weekStart, now);
        PeriodSnapshot month = periodSnapshot(monthStart, now);

        Map<String, WorkflowTypeStats> byWorkflowRaw = workflowUsageRepository
                .aggregateByWorkflowInRange(startDt, endDt)
                .stream()
                .collect(Collectors.toMap(
                        row -> row[0].toString(),
                        row -> WorkflowTypeStats.builder()
                                .workflow(row[0].toString())
                                .runCount(((Number) row[1]).longValue())
                                .totalDurationMs(((Number) row[2]).longValue())
                                .avgDurationMs(((Number) row[3]).doubleValue())
                                .build(),
                        (a, b) -> a,
                        LinkedHashMap::new));

        Map<String, WorkflowTypeStats> byWorkflow = fillAllWorkflowTypes(byWorkflowRaw);

        Map<LocalDate, DailyWorkflowStats> dailyStats = workflowUsageRepository
                .getDailyWorkflowStats(startDt, endDt)
                .stream()
                .collect(Collectors.toMap(
                        row -> toLocalDate(row[0]),
                        row -> DailyWorkflowStats.builder()
                                .runCount(((Number) row[1]).longValue())
                                .totalDurationMs(((Number) row[2]).longValue())
                                .build(),
                        (a, b) -> a,
                        LinkedHashMap::new));

        List<WorkflowUserActivity> topUsers = workflowUsageRepository
                .topUsersByWorkflowTime(startDt, endDt, PageRequest.of(0, 10))
                .stream()
                .map(row -> WorkflowUserActivity.builder()
                        .userId(((Number) row[0]).longValue())
                        .email((String) row[1])
                        .runCount(((Number) row[2]).longValue())
                        .totalDurationMs(((Number) row[3]).longValue())
                        .build())
                .collect(Collectors.toList());

        Map<String, Long> failedRunsByWorkflow = workflowUsageRepository
                .failedCountByWorkflowInRange(startDt, endDt)
                .stream()
                .collect(Collectors.toMap(
                        row -> row[0].toString(),
                        row -> ((Number) row[1]).longValue()));

        WorkflowToolStats regen = buildToolStats(
                WorkflowType.REGEN, startDt, endDt, todayStart, weekStart, monthStart, now, failedRunsByWorkflow);
        WorkflowToolStats prepareRegen = buildToolStats(
                WorkflowType.PREPARE_REGEN, startDt, endDt, todayStart, weekStart, monthStart, now, failedRunsByWorkflow);
        WorkflowToolStats extractLayers = buildToolStats(
                WorkflowType.EXTRACT_LAYERS, startDt, endDt, todayStart, weekStart, monthStart, now, failedRunsByWorkflow);

        return WorkflowUsageAnalytics.builder()
                .totalRuns(totalRuns)
                .totalDurationMs(totalDurationMs)
                .activeUsers(activeUsers != null ? activeUsers : 0L)
                .runsToday(today.runs())
                .runsThisWeek(week.runs())
                .runsThisMonth(month.runs())
                .durationMsToday(today.durationMs())
                .durationMsThisWeek(week.durationMs())
                .durationMsThisMonth(month.durationMs())
                .byWorkflow(byWorkflow)
                .dailyStats(dailyStats)
                .topUsersByWorkflowTime(topUsers)
                .failedRunsByWorkflow(failedRunsByWorkflow)
                .regen(regen)
                .prepareRegen(prepareRegen)
                .extractLayers(extractLayers)
                .build();
    }

    private static final WorkflowType[] REPORTED_WORKFLOWS = {
            WorkflowType.CHAT,
            WorkflowType.ANALYZE,
            WorkflowType.GENERATE,
            WorkflowType.REGEN,
            WorkflowType.PREPARE_REGEN,
            WorkflowType.SUGGEST_STYLE,
            WorkflowType.EXTRACT_LAYERS,
    };

    private Map<String, WorkflowTypeStats> fillAllWorkflowTypes(Map<String, WorkflowTypeStats> existing) {
        Map<String, WorkflowTypeStats> result = new LinkedHashMap<>();
        for (WorkflowType type : REPORTED_WORKFLOWS) {
            String key = type.name();
            result.put(key, existing.getOrDefault(key, WorkflowTypeStats.builder()
                    .workflow(key)
                    .runCount(0L)
                    .totalDurationMs(0L)
                    .avgDurationMs(0.0)
                    .build()));
        }
        existing.forEach((k, v) -> {
            if (!result.containsKey(k)) {
                result.put(k, v);
            }
        });
        return result;
    }

    private WorkflowToolStats buildToolStats(
            WorkflowType workflow,
            LocalDateTime periodStart,
            LocalDateTime periodEnd,
            LocalDateTime todayStart,
            LocalDateTime weekStart,
            LocalDateTime monthStart,
            LocalDateTime now,
            Map<String, Long> failedRunsByWorkflow) {
        Object[] period = workflowUsageRepository.statsForWorkflowInRange(periodStart, periodEnd, workflow);
        long durationMs = period[0] != null ? ((Number) period[0]).longValue() : 0L;
        long runCount = period[1] != null ? ((Number) period[1]).longValue() : 0L;
        double avgMs = period[2] != null ? ((Number) period[2]).doubleValue() : 0.0;

        long runsToday = countRunsForWorkflow(workflow, todayStart, now);
        long runsWeek = countRunsForWorkflow(workflow, weekStart, now);
        long runsMonth = countRunsForWorkflow(workflow, monthStart, now);

        Long failed = failedRunsByWorkflow.get(workflow.name());
        if (failed == null) {
            Long q = workflowUsageRepository.failedCountForWorkflowInRange(periodStart, periodEnd, workflow);
            failed = q != null ? q : 0L;
        }

        return WorkflowToolStats.builder()
                .workflow(workflow.name())
                .runCount(runCount)
                .totalDurationMs(durationMs)
                .avgDurationMs(avgMs)
                .runsToday(runsToday)
                .runsThisWeek(runsWeek)
                .runsThisMonth(runsMonth)
                .failedRuns(failed)
                .build();
    }

    private long countRunsForWorkflow(WorkflowType workflow, LocalDateTime from, LocalDateTime to) {
        Object[] totals = workflowUsageRepository.statsForWorkflowInRange(from, to, workflow);
        return totals[1] != null ? ((Number) totals[1]).longValue() : 0L;
    }

    private PeriodSnapshot periodSnapshot(LocalDateTime from, LocalDateTime to) {
        Object[] totals = workflowUsageRepository.totalDurationAndCountInRange(from, to);
        long durationMs = totals[0] != null ? ((Number) totals[0]).longValue() : 0L;
        long runs = totals[1] != null ? ((Number) totals[1]).longValue() : 0L;
        return new PeriodSnapshot(runs, durationMs);
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.util.Date utilDate) {
            return new java.sql.Date(utilDate.getTime()).toLocalDate();
        }
        throw new IllegalArgumentException("Unsupported date type: " + value.getClass());
    }

    private record PeriodSnapshot(long runs, long durationMs) {}

    /** Bổ sung regen / tách layer vào map feature cũ (ai_token_usages không có các loại này). */
    private Map<String, Long> enrichFeatureUsageWithWorkflows(
            Map<String, Long> featureUsage,
            WorkflowUsageAnalytics workflowUsage) {
        Map<String, Long> merged = new LinkedHashMap<>(featureUsage != null ? featureUsage : Map.of());
        if (workflowUsage == null) {
            return merged;
        }
        putWorkflowRunCount(merged, workflowUsage.getRegen());
        putWorkflowRunCount(merged, workflowUsage.getPrepareRegen());
        putWorkflowRunCount(merged, workflowUsage.getExtractLayers());
        return merged;
    }

    private void putWorkflowRunCount(Map<String, Long> merged, WorkflowToolStats stats) {
        if (stats == null || stats.getRunCount() == null || stats.getRunCount() <= 0) {
            return;
        }
        merged.put(stats.getWorkflow(), stats.getRunCount());
    }
    
    private Map<LocalDate, Long> getDailyChatCounts(LocalDateTime startDt, LocalDateTime endDt) {
        List<Object[]> results = analyticsRepository.getChatCountByDate(startDt, endDt);
        
        return results.stream()
            .collect(Collectors.toMap(
                row -> ((java.sql.Date) row[0]).toLocalDate(),
                row -> ((Number) row[1]).longValue()
            ));
    }
    
    private List<UserActivityDTO> getTopActiveUsers(LocalDateTime startDt, int limit) {
        List<Object[]> results = analyticsRepository.getTopActiveUsers(startDt, limit);
        
        return results.stream()
            .map(row -> UserActivityDTO.builder()
                .userId(((Number) row[0]).longValue())
                .email((String) row[1])
                .planName((String) row[2])
                .chatCount(((Number) row[3]).longValue())
                .aiTokensUsed(((Number) row[4]).longValue())
                .build())
            .collect(Collectors.toList());
    }
    
    private Map<String, Long> getFeatureUsage(LocalDateTime startDt) {
        List<Object[]> results = analyticsRepository.getFeatureUsageStats(startDt);
        
        return results.stream()
            .collect(Collectors.toMap(
                row -> (String) row[0],
                row -> ((Number) row[1]).longValue()
            ));
    }
    
    private Map<String, Long> getUsersByPlan() {
        List<Object[]> results = analyticsRepository.getUsersByPlan();
        
        return results.stream()
            .collect(Collectors.toMap(
                row -> (String) row[0],
                row -> ((Number) row[1]).longValue()
            ));
    }
    
    private Map<String, Long> getChatsByPlan(LocalDateTime startDt) {
        List<Object[]> results = analyticsRepository.getChatsByPlan(startDt);
        
        return results.stream()
            .collect(Collectors.toMap(
                row -> (String) row[0],
                row -> ((Number) row[1]).longValue()
            ));
    }
}
