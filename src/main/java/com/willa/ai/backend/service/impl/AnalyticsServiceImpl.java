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
        return getAnalytics(startDate, LocalDate.now());
    }

    @Override
    public AnalyticsResponse getAnalytics(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must be on or after startDate");
        }
        LocalDateTime startDt = startDate.atStartOfDay();
        LocalDateTime endDt = endDate.atTime(LocalTime.MAX);
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
        Long totalActiveUsers = analyticsRepository.getActiveUserCount(startDt, endDt);
        Long totalChatsInPeriod = analyticsRepository.getChatCount(startDt, endDt);
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
        
        // Tất cả user có chat trong kỳ
        List<UserActivityDTO> topActiveUsers = getActiveUsersInPeriod(startDt, endDt);
        
        // Feature usage
        Map<String, Long> featureUsageByActionType = getFeatureUsage(startDt, endDt);
        
        // Users by plan
        Map<String, Long> usersByPlan = getUsersByPlan();
        
        // Chats by plan
        Map<String, Long> chatsByPlan = getChatsByPlan(startDt, endDt);

        Long newRegistrations = analyticsRepository.countNewRegistrations(startDt, endDt);
        Map<String, Long> feedbackPlanStarts = getFeedbackPlanStartsInPeriod(startDt, endDt);
        Long totalAiTokens = analyticsRepository.sumTokensInPeriod(startDt, endDt);

        WorkflowUsageAnalytics workflowUsage = buildWorkflowUsageAnalytics(startDt, endDt);
        featureUsageByActionType = enrichFeatureUsageWithWorkflows(featureUsageByActionType, workflowUsage);
        
        return AnalyticsResponse.builder()
            .totalActiveUsers(totalActiveUsers != null ? totalActiveUsers : 0)
            .totalChatsToday(totalChatsToday != null ? totalChatsToday : 0)
            .totalChatsThisWeek(totalChatsThisWeek != null ? totalChatsThisWeek : 0)
            .totalChatsThisMonth(totalChatsThisMonth != null ? totalChatsThisMonth : 0)
            .totalChatsInPeriod(totalChatsInPeriod != null ? totalChatsInPeriod : 0)
            .newRegistrationsInPeriod(newRegistrations != null ? newRegistrations : 0)
            .feedbackPlanStartsInPeriod(feedbackPlanStarts)
            .totalAiTokensInPeriod(totalAiTokens != null ? totalAiTokens : 0)
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

        Object[] periodTotals = unwrapSingletonRow(
                workflowUsageRepository.totalDurationAndCountInRange(startDt, endDt));
        long totalDurationMs = asLong(periodTotals[0]);
        long totalRuns = asLong(periodTotals[1]);

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
                                .runCount(asLong(row[1]))
                                .totalDurationMs(asLong(row[2]))
                                .avgDurationMs(asDouble(row[3]))
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
                                .runCount(asLong(row[1]))
                                .totalDurationMs(asLong(row[2]))
                                .build(),
                        (a, b) -> a,
                        LinkedHashMap::new));

        Map<Long, String> highestPlanByUser = getHighestFeedbackPlanMap(startDt, endDt);
        Map<Long, Long> tokensByUser = getTokensByUserMap(startDt, endDt);

        List<Object[]> engagementRows = workflowUsageRepository.userAiEngagementInRange(startDt, endDt);
        Map<Long, long[]> engagementByUser = new HashMap<>();
        double sumActiveDays = 0;
        double sumInactiveDays = 0;
        double sumActiveDaysBeforeInactive = 0;
        long inactiveUserCount = 0;
        for (Object[] row : engagementRows) {
            Long userId = asLong(row[0]);
            long activeDays = asLong(row[1]);
            long daysInactive = asLong(row[5]);
            engagementByUser.put(userId, new long[]{activeDays, daysInactive});
            sumActiveDays += activeDays;
            sumInactiveDays += daysInactive;
            if (daysInactive > 0) {
                sumActiveDaysBeforeInactive += activeDays;
                inactiveUserCount++;
            }
        }
        int engagementUserCount = engagementRows.size();
        Double avgActiveDays = engagementUserCount > 0
                ? round1(sumActiveDays / engagementUserCount) : 0.0;
        Double avgInactiveDays = engagementUserCount > 0
                ? round1(sumInactiveDays / engagementUserCount) : 0.0;
        Double avgUsedBeforeInactive = inactiveUserCount > 0
                ? round1(sumActiveDaysBeforeInactive / inactiveUserCount) : 0.0;

        List<WorkflowUserActivity> topUsers = workflowUsageRepository
                .usersByWorkflowTimeInRange(startDt, endDt)
                .stream()
                .map(row -> {
                    Long userId = asLong(row[0]);
                    long[] eng = engagementByUser.getOrDefault(userId, new long[]{0L, 0L});
                    return WorkflowUserActivity.builder()
                            .userId(userId)
                            .email((String) row[1])
                            .planName(highestPlanByUser.getOrDefault(userId, "Free"))
                            .runCount(asLong(row[2]))
                            .totalDurationMs(asLong(row[3]))
                            .aiTokensUsed(tokensByUser.getOrDefault(userId, 0L))
                            .activeDaysInPeriod(eng[0])
                            .daysInactiveUntilNow(eng[1])
                            .build();
                })
                .collect(Collectors.toList());

        Map<String, Long> failedRunsByWorkflow = workflowUsageRepository
                .failedCountByWorkflowInRange(startDt, endDt)
                .stream()
                .collect(Collectors.toMap(
                        row -> row[0].toString(),
                        row -> asLong(row[1])));

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
                .avgActiveDaysInPeriod(avgActiveDays)
                .avgDaysInactiveUntilNow(avgInactiveDays)
                .avgDaysUsedBeforeInactive(avgUsedBeforeInactive)
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
            WorkflowType.WORKSPACE,
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
        Object[] period = unwrapSingletonRow(
                workflowUsageRepository.statsForWorkflowInRange(periodStart, periodEnd, workflow));
        long durationMs = asLong(period[0]);
        long runCount = asLong(period[1]);
        double avgMs = asDouble(period[2]);

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
        Object[] totals = unwrapSingletonRow(
                workflowUsageRepository.statsForWorkflowInRange(from, to, workflow));
        return asLong(totals[1]);
    }

    private PeriodSnapshot periodSnapshot(LocalDateTime from, LocalDateTime to) {
        Object[] totals = unwrapSingletonRow(
                workflowUsageRepository.totalDurationAndCountInRange(from, to));
        return new PeriodSnapshot(asLong(totals[1]), asLong(totals[0]));
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

    /** Hibernate/Spring Data đôi khi bọc kết quả aggregate 1 dòng thành Object[] lồng nhau. */
    private static Object[] unwrapSingletonRow(Object[] row) {
        if (row != null && row.length == 1 && row[0] instanceof Object[] nested) {
            return nested;
        }
        return row;
    }

    private static long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof Object[] arr) {
            return arr.length > 0 ? asLong(arr[0]) : 0L;
        }
        throw new IllegalArgumentException("Cannot convert to long: " + value.getClass().getName());
    }

    private static double asDouble(Object value) {
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof Object[] arr) {
            return arr.length > 0 ? asDouble(arr[0]) : 0.0;
        }
        throw new IllegalArgumentException("Cannot convert to double: " + value.getClass().getName());
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

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

    private static String mapKey(Object value) {
        return value != null ? value.toString() : "Unknown";
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
                row -> toLocalDate(row[0]),
                row -> asLong(row[1])
            ));
    }
    
    private List<UserActivityDTO> getActiveUsersInPeriod(LocalDateTime startDt, LocalDateTime endDt) {
        List<Object[]> results = analyticsRepository.getActiveUsersInPeriod(startDt, endDt);
        
        return results.stream()
            .map(row -> UserActivityDTO.builder()
                .userId(asLong(row[0]))
                .email((String) row[1])
                .planName(row[2] != null ? row[2].toString() : "Free")
                .chatCount(asLong(row[3]))
                .aiTokensUsed(asLong(row[4]))
                .build())
            .collect(Collectors.toList());
    }

    private Map<String, Long> getFeedbackPlanStartsInPeriod(LocalDateTime startDt, LocalDateTime endDt) {
        Map<String, Long> starts = new LinkedHashMap<>();
        starts.put("Free", 0L);
        starts.put("Student", 0L);
        starts.put("Pro", 0L);
        for (Object[] row : analyticsRepository.countFeedbackPlanStartsInPeriod(startDt, endDt)) {
            String tier = row[0] != null ? row[0].toString() : "Free";
            starts.put(tier, asLong(row[1]));
        }
        return starts;
    }

    private Map<Long, String> getHighestFeedbackPlanMap(LocalDateTime startDt, LocalDateTime endDt) {
        return analyticsRepository.getHighestFeedbackPlanByUserInPeriod(startDt, endDt).stream()
                .collect(Collectors.toMap(
                        row -> asLong(row[0]),
                        row -> row[1] != null ? row[1].toString() : "Free",
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    private Map<Long, Long> getTokensByUserMap(LocalDateTime startDt, LocalDateTime endDt) {
        return analyticsRepository.sumTokensByUserInPeriod(startDt, endDt).stream()
                .collect(Collectors.toMap(
                        row -> asLong(row[0]),
                        row -> asLong(row[1]),
                        Long::sum,
                        LinkedHashMap::new));
    }
    
    private Map<String, Long> getFeatureUsage(LocalDateTime startDt, LocalDateTime endDt) {
        List<Object[]> results = analyticsRepository.getFeatureUsageStats(startDt, endDt);
        
        return results.stream()
            .collect(Collectors.toMap(
                row -> mapKey(row[0]),
                row -> asLong(row[1])
            ));
    }
    
    private Map<String, Long> getUsersByPlan() {
        List<Object[]> results = analyticsRepository.getUsersByPlan();
        
        return results.stream()
            .collect(Collectors.toMap(
                row -> mapKey(row[0]),
                row -> asLong(row[1]),
                Long::sum
            ));
    }
    
    private Map<String, Long> getChatsByPlan(LocalDateTime startDt, LocalDateTime endDt) {
        List<Object[]> results = analyticsRepository.getChatsByPlan(startDt, endDt);
        
        return results.stream()
            .collect(Collectors.toMap(
                row -> mapKey(row[0]),
                row -> asLong(row[1]),
                Long::sum
            ));
    }
}
