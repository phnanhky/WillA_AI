package com.willa.ai.backend.service.impl;

import com.willa.ai.backend.config.AnalyticsExcludedUsersProperties;
import com.willa.ai.backend.dto.response.AnalyticsResponse;
import com.willa.ai.backend.dto.response.AnalyticsResponse.DailyWorkflowStats;
import com.willa.ai.backend.dto.response.AnalyticsResponse.ExpertAnalytics;
import com.willa.ai.backend.dto.response.AnalyticsResponse.ExpertCompletedJobRow;
import com.willa.ai.backend.dto.response.AnalyticsResponse.ExpertLeaderboardRow;
import com.willa.ai.backend.dto.response.AnalyticsResponse.ExpertPayrollRow;
import com.willa.ai.backend.dto.response.AnalyticsResponse.UserActivityDTO;
import com.willa.ai.backend.dto.response.AnalyticsResponse.WorkflowToolStats;
import com.willa.ai.backend.dto.response.AnalyticsResponse.WorkflowTypeStats;
import com.willa.ai.backend.dto.response.AnalyticsResponse.WorkflowUsageAnalytics;
import com.willa.ai.backend.dto.response.AnalyticsResponse.WorkflowUserActivity;
import com.willa.ai.backend.dto.response.AnalyticsResponse.WorkspaceActivityRow;
import com.willa.ai.backend.dto.response.AnalyticsResponse.WorkspaceAnalytics;
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
    private final AnalyticsExcludedUsersProperties excludedUsers;

    private Collection<Long> excludedIds() {
        return excludedUsers.queryIds();
    }
    
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
        Collection<Long> excluded = excludedIds();
        Long totalActiveUsers = analyticsRepository.getActiveUserCount(startDt, endDt, excluded);
        Long totalChatsInPeriod = analyticsRepository.getChatCount(startDt, endDt, excluded);
        Long totalChatsToday = analyticsRepository.getChatCount(
            LocalDate.now().atStartOfDay(),
            LocalDateTime.now(),
            excluded
        );
        Long totalChatsThisWeek = analyticsRepository.getChatCount(
            LocalDate.now().minusDays(7).atStartOfDay(),
            LocalDateTime.now(),
            excluded
        );
        Long totalChatsThisMonth = analyticsRepository.getChatCount(
            LocalDate.now().minusDays(30).atStartOfDay(),
            LocalDateTime.now(),
            excluded
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

        Long newRegistrations = analyticsRepository.countNewRegistrations(startDt, endDt, excludedIds());
        Map<String, Long> feedbackPlanStarts = getFeedbackPlanStartsInPeriod(startDt, endDt);
        Long totalAiTokens = analyticsRepository.sumTokensInPeriod(startDt, endDt, excludedIds());

        WorkflowUsageAnalytics workflowUsage = buildWorkflowUsageAnalytics(startDt, endDt);
        featureUsageByActionType = enrichFeatureUsageWithWorkflows(featureUsageByActionType, workflowUsage);
        ExpertAnalytics expertAnalytics = buildExpertAnalytics(startDt, endDt);
        WorkspaceAnalytics workspaceAnalytics = buildWorkspaceAnalytics(startDt, endDt);
        
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
            .expertAnalytics(expertAnalytics)
            .workspaceAnalytics(workspaceAnalytics)
            .build();
    }

    private ExpertAnalytics buildExpertAnalytics(LocalDateTime startDt, LocalDateTime endDt) {
        Long bookings = nz(analyticsRepository.countExpertBookingsInPeriod(startDt, endDt));
        Long completed = nz(analyticsRepository.countExpertCompletedInPeriod(startDt, endDt));
        Long revenue = nz(analyticsRepository.sumExpertPaidRevenueInPeriod(startDt, endDt));
        Long payableGross = nz(analyticsRepository.sumExpertPayableGrossInPeriod(startDt, endDt));
        Long payableHours = nz(analyticsRepository.sumExpertPayableHourlyHoursInPeriod(startDt, endDt));
        Long clients = nz(analyticsRepository.countExpertUniqueClientsInPeriod(startDt, endDt));
        Long expertsBooked = nz(analyticsRepository.countExpertUniqueBookedInPeriod(startDt, endDt));
        Long activeExperts = nz(analyticsRepository.countActiveExperts());
        Long messages = nz(analyticsRepository.countExpertMessagesInPeriod(startDt, endDt));

        long callSessions = 0;
        long callSeconds = 0;
        List<Object[]> callRows = analyticsRepository.expertCallSessionStatsInPeriod(startDt, endDt);
        if (callRows != null && !callRows.isEmpty() && callRows.get(0) != null) {
            Object[] row = callRows.get(0);
            callSessions = asLong(row[0]);
            callSeconds = asLong(row[1]);
        }

        Map<String, Long> byStatus = toStringLongMap(
                analyticsRepository.countExpertBookingsByStatus(startDt, endDt));
        Map<String, Long> byType = toStringLongMap(
                analyticsRepository.countExpertBookingsByType(startDt, endDt));

        List<ExpertPayrollRow> payroll = analyticsRepository.expertPayrollInPeriod(startDt, endDt)
                .stream()
                .map(row -> ExpertPayrollRow.builder()
                        .expertId(asLong(row[0]))
                        .expertName((String) row[1])
                        .email((String) row[2])
                        .completedCount(asLong(row[3]))
                        .reviewCount(asLong(row[4]))
                        .reviewGrossVnd(asLong(row[5]))
                        .hourlyCount(asLong(row[6]))
                        .hourlyHours(asLong(row[7]))
                        .hourlyGrossVnd(asLong(row[8]))
                        .payableGrossVnd(asLong(row[9]))
                        .callDurationSeconds(asLong(row[10]))
                        .build())
                .collect(Collectors.toList());

        List<ExpertCompletedJobRow> jobs = analyticsRepository.expertCompletedJobsInPeriod(startDt, endDt)
                .stream()
                .map(row -> ExpertCompletedJobRow.builder()
                        .bookingId(asLong(row[0]))
                        .expertId(asLong(row[1]))
                        .expertName((String) row[2])
                        .expertEmail((String) row[3])
                        .clientEmail((String) row[4])
                        .bookingType(row[5] != null ? row[5].toString() : null)
                        .hourlyHours(row[6] != null ? ((Number) row[6]).intValue() : null)
                        .amountVnd(asLong(row[7]))
                        .completedAt(formatDateTime(row[8]))
                        .build())
                .collect(Collectors.toList());

        List<ExpertLeaderboardRow> topExperts = payroll.stream()
                .map(p -> ExpertLeaderboardRow.builder()
                        .expertId(p.getExpertId())
                        .expertName(p.getExpertName())
                        .email(p.getEmail())
                        .bookingCount(p.getCompletedCount())
                        .paidRevenueVnd(p.getPayableGrossVnd())
                        .completedCount(p.getCompletedCount())
                        .build())
                .limit(20)
                .collect(Collectors.toList());

        return ExpertAnalytics.builder()
                .bookingsInPeriod(bookings)
                .completedInPeriod(completed)
                .paidRevenueVnd(revenue)
                .payableGrossVnd(payableGross)
                .payableHourlyHours(payableHours)
                .uniqueClients(clients)
                .uniqueExpertsBooked(expertsBooked)
                .activeExpertsTotal(activeExperts)
                .messagesInPeriod(messages)
                .callSessionsInPeriod(callSessions)
                .callDurationSecondsInPeriod(callSeconds)
                .bookingsByStatus(byStatus)
                .bookingsByType(byType)
                .payrollByExpert(payroll)
                .completedJobs(jobs)
                .topExperts(topExperts)
                .build();
    }

    private static String formatDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime ldt) {
            return ldt.toString();
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().toString();
        }
        return value.toString();
    }

    private WorkspaceAnalytics buildWorkspaceAnalytics(LocalDateTime startDt, LocalDateTime endDt) {
        List<WorkspaceActivityRow> top = analyticsRepository.topWorkspacesInPeriod(startDt, endDt)
                .stream()
                .map(row -> WorkspaceActivityRow.builder()
                        .workspaceId(asLong(row[0]))
                        .title((String) row[1])
                        .ownerEmail((String) row[2])
                        .memberCount(asLong(row[3]))
                        .channelMessagesInPeriod(asLong(row[4]))
                        .build())
                .collect(Collectors.toList());

        return WorkspaceAnalytics.builder()
                .workspacesCreatedInPeriod(nz(analyticsRepository.countWorkspacesCreatedInPeriod(startDt, endDt)))
                .totalWorkspaces(nz(analyticsRepository.countTotalWorkspaces()))
                .membersJoinedInPeriod(nz(analyticsRepository.countMembersJoinedInPeriod(startDt, endDt)))
                .totalMembers(nz(analyticsRepository.countTotalWorkspaceMembers()))
                .channelMessagesInPeriod(nz(analyticsRepository.countChannelMessagesInPeriod(startDt, endDt)))
                .dmMessagesInPeriod(nz(analyticsRepository.countDmMessagesInPeriod(startDt, endDt)))
                .activeSubscriptions(nz(analyticsRepository.countActiveWorkspaceSubscriptions()))
                .planStartsInPeriod(nz(analyticsRepository.countWorkspacePlanStartsInPeriod(startDt, endDt)))
                .projectsCreatedInPeriod(nz(analyticsRepository.countProjectsCreatedInPeriod(startDt, endDt)))
                .topWorkspaces(top)
                .build();
    }

    private static long nz(Long v) {
        return v != null ? v : 0L;
    }

    private static Map<String, Long> toStringLongMap(List<Object[]> rows) {
        if (rows == null || rows.isEmpty()) return new LinkedHashMap<>();
        return rows.stream()
                .filter(r -> r != null && r.length >= 2 && r[0] != null)
                .collect(Collectors.toMap(
                        r -> r[0].toString(),
                        r -> asLong(r[1]),
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    private WorkflowUsageAnalytics buildWorkflowUsageAnalytics(LocalDateTime startDt, LocalDateTime endDt) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime weekStart = LocalDate.now().minusDays(7).atStartOfDay();
        LocalDateTime monthStart = LocalDate.now().minusDays(30).atStartOfDay();

        Collection<Long> excluded = excludedIds();
        Object[] periodTotals = unwrapSingletonRow(
                workflowUsageRepository.totalDurationAndCountInRange(startDt, endDt, excluded));
        long totalDurationMs = asLong(periodTotals[0]);
        long totalRuns = asLong(periodTotals[1]);

        Long activeUsers = workflowUsageRepository.countDistinctUsersInRange(startDt, endDt, excluded);

        PeriodSnapshot today = periodSnapshot(todayStart, now);
        PeriodSnapshot week = periodSnapshot(weekStart, now);
        PeriodSnapshot month = periodSnapshot(monthStart, now);

        Map<String, WorkflowTypeStats> byWorkflowRaw = workflowUsageRepository
                .aggregateByWorkflowInRange(startDt, endDt, excluded)
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
                .getDailyWorkflowStats(startDt, endDt, excluded)
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

        List<Object[]> engagementRows = workflowUsageRepository.userAiEngagementInRange(startDt, endDt, excluded);
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
                .usersByWorkflowTimeInRange(startDt, endDt, excluded)
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
                .failedCountByWorkflowInRange(startDt, endDt, excluded)
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
                workflowUsageRepository.statsForWorkflowInRange(periodStart, periodEnd, workflow, excludedIds()));
        long durationMs = asLong(period[0]);
        long runCount = asLong(period[1]);
        double avgMs = asDouble(period[2]);

        long runsToday = countRunsForWorkflow(workflow, todayStart, now);
        long runsWeek = countRunsForWorkflow(workflow, weekStart, now);
        long runsMonth = countRunsForWorkflow(workflow, monthStart, now);

        Long failed = failedRunsByWorkflow.get(workflow.name());
        if (failed == null) {
            Long q = workflowUsageRepository.failedCountForWorkflowInRange(
                    periodStart, periodEnd, workflow, excludedIds());
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
                workflowUsageRepository.statsForWorkflowInRange(from, to, workflow, excludedIds()));
        return asLong(totals[1]);
    }

    private PeriodSnapshot periodSnapshot(LocalDateTime from, LocalDateTime to) {
        Object[] totals = unwrapSingletonRow(
                workflowUsageRepository.totalDurationAndCountInRange(from, to, excludedIds()));
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
        List<Object[]> results = analyticsRepository.getChatCountByDate(startDt, endDt, excludedIds());
        
        return results.stream()
            .collect(Collectors.toMap(
                row -> toLocalDate(row[0]),
                row -> asLong(row[1])
            ));
    }
    
    private List<UserActivityDTO> getActiveUsersInPeriod(LocalDateTime startDt, LocalDateTime endDt) {
        List<Object[]> results = analyticsRepository.getActiveUsersInPeriod(startDt, endDt, excludedIds());
        
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
        for (Object[] row : analyticsRepository.countFeedbackPlanStartsInPeriod(startDt, endDt, excludedIds())) {
            String tier = row[0] != null ? row[0].toString() : "Free";
            starts.put(tier, asLong(row[1]));
        }
        return starts;
    }

    private Map<Long, String> getHighestFeedbackPlanMap(LocalDateTime startDt, LocalDateTime endDt) {
        return analyticsRepository.getHighestFeedbackPlanByUserInPeriod(startDt, endDt, excludedIds()).stream()
                .collect(Collectors.toMap(
                        row -> asLong(row[0]),
                        row -> row[1] != null ? row[1].toString() : "Free",
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    private Map<Long, Long> getTokensByUserMap(LocalDateTime startDt, LocalDateTime endDt) {
        return analyticsRepository.sumTokensByUserInPeriod(startDt, endDt, excludedIds()).stream()
                .collect(Collectors.toMap(
                        row -> asLong(row[0]),
                        row -> asLong(row[1]),
                        Long::sum,
                        LinkedHashMap::new));
    }
    
    private Map<String, Long> getFeatureUsage(LocalDateTime startDt, LocalDateTime endDt) {
        List<Object[]> results = analyticsRepository.getFeatureUsageStats(startDt, endDt, excludedIds());
        
        return results.stream()
            .collect(Collectors.toMap(
                row -> mapKey(row[0]),
                row -> asLong(row[1])
            ));
    }
    
    private Map<String, Long> getUsersByPlan() {
        List<Object[]> results = analyticsRepository.getUsersByPlan(excludedIds());
        
        return results.stream()
            .collect(Collectors.toMap(
                row -> mapKey(row[0]),
                row -> asLong(row[1]),
                Long::sum
            ));
    }
    
    private Map<String, Long> getChatsByPlan(LocalDateTime startDt, LocalDateTime endDt) {
        List<Object[]> results = analyticsRepository.getChatsByPlan(startDt, endDt, excludedIds());
        
        return results.stream()
            .collect(Collectors.toMap(
                row -> mapKey(row[0]),
                row -> asLong(row[1]),
                Long::sum
            ));
    }
}
