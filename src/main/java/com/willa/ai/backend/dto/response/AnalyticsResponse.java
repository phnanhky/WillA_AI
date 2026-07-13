package com.willa.ai.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsResponse {
    
    // Tổng quan
    private Long totalActiveUsers;
    private Long totalChatsToday;
    private Long totalChatsThisWeek;
    private Long totalChatsThisMonth;
    /** Tổng chat trong khoảng báo cáo (start–end) */
    private Long totalChatsInPeriod;

    /** Số user đăng ký mới trong kỳ (users.created_at). */
    private Long newRegistrationsInPeriod;

    /**
     * Số user bắt đầu gói Feedback trong kỳ (subscription.start_date).
     * Keys: Free, Student, Pro.
     */
    private Map<String, Long> feedbackPlanStartsInPeriod;

    /** Tổng token AI (ai_token_usages) trong kỳ lọc. */
    private Long totalAiTokensInPeriod;
    
    // Chi tiết người dùng
    private List<UserActivityDTO> topActiveUsers;
    private Map<LocalDate, Long> dailyChatCounts; // Số chat mỗi ngày
    
    // Chức năng được dùng nhiều
    private Map<String, Long> featureUsageByActionType;
    
    // Phân tích theo subscription plan
    private Map<String, Long> usersByPlan;
    private Map<String, Long> chatsByPlan;

    /** Thời gian & số lần chạy workflow AI (CHAT, ANALYZE, GENERATE, …) */
    private WorkflowUsageAnalytics workflowUsage;

    /** Analytics module Expert (bookings, chat, call). */
    private ExpertAnalytics expertAnalytics;

    /** Analytics module Workspace (workspaces, members, chat, subscriptions). */
    private WorkspaceAnalytics workspaceAnalytics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpertAnalytics {
        /** Booking tạo trong kỳ. */
        private Long bookingsInPeriod;
        /** Booking COMPLETED trong kỳ (completed_at) — cơ sở trả lương. */
        private Long completedInPeriod;
        /** Doanh thu khách đã trả trong kỳ (booking tạo + status đã thanh toán). */
        private Long paidRevenueVnd;
        /**
         * Tổng tiền cần tính lương: SUM(amount_vnd) của booking COMPLETED
         * theo completed_at trong kỳ.
         */
        private Long payableGrossVnd;
        /** Số giờ HOURLY đã hoàn tất trong kỳ. */
        private Long payableHourlyHours;
        private Long uniqueClients;
        private Long uniqueExpertsBooked;
        /** Expert đang active (toàn hệ thống). */
        private Long activeExpertsTotal;
        private Long messagesInPeriod;
        private Long callSessionsInPeriod;
        private Long callDurationSecondsInPeriod;
        /** status → count (booking created trong kỳ). */
        private Map<String, Long> bookingsByStatus;
        /** REVIEW / HOURLY → count. */
        private Map<String, Long> bookingsByType;
        /** Bảng lương theo expert (COMPLETED trong kỳ). */
        private List<ExpertPayrollRow> payrollByExpert;
        /** Chi tiết từng booking đã hoàn tất — đối chiếu trả lương. */
        private List<ExpertCompletedJobRow> completedJobs;
        /** @deprecated dùng payrollByExpert */
        private List<ExpertLeaderboardRow> topExperts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpertPayrollRow {
        private Long expertId;
        private String expertName;
        private String email;
        private Long completedCount;
        private Long reviewCount;
        private Long reviewGrossVnd;
        private Long hourlyCount;
        private Long hourlyHours;
        private Long hourlyGrossVnd;
        /** Tổng amount_vnd COMPLETED — gross trước khi chia %. */
        private Long payableGrossVnd;
        private Long callDurationSeconds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpertCompletedJobRow {
        private Long bookingId;
        private Long expertId;
        private String expertName;
        private String expertEmail;
        private String clientEmail;
        private String bookingType;
        private Integer hourlyHours;
        private Long amountVnd;
        private String completedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpertLeaderboardRow {
        private Long expertId;
        private String expertName;
        private String email;
        private Long bookingCount;
        private Long paidRevenueVnd;
        private Long completedCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkspaceAnalytics {
        private Long workspacesCreatedInPeriod;
        private Long totalWorkspaces;
        private Long membersJoinedInPeriod;
        private Long totalMembers;
        private Long channelMessagesInPeriod;
        private Long dmMessagesInPeriod;
        private Long activeSubscriptions;
        /** User bắt đầu gói Workspace trong kỳ (start_date). */
        private Long planStartsInPeriod;
        private Long projectsCreatedInPeriod;
        private List<WorkspaceActivityRow> topWorkspaces;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkspaceActivityRow {
        private Long workspaceId;
        private String title;
        private String ownerEmail;
        private Long memberCount;
        private Long channelMessagesInPeriod;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowUsageAnalytics {
        private Long totalRuns;
        private Long totalDurationMs;
        private Long activeUsers;
        private Long runsToday;
        private Long runsThisWeek;
        private Long runsThisMonth;
        private Long durationMsToday;
        private Long durationMsThisWeek;
        private Long durationMsThisMonth;
        /**
         * Trung bình số ngày DISTINCT có dùng AI trong kỳ (trên các user AI của kỳ).
         */
        private Double avgActiveDaysInPeriod;
        /**
         * Trung bình số ngày không dùng AI kể từ lần cuối → hôm nay.
         */
        private Double avgDaysInactiveUntilNow;
        /**
         * Trung bình số ngày DISTINCT đã dùng trong kỳ, chỉ trên user đã ngừng (inactive ≥ 1 ngày).
         */
        private Double avgDaysUsedBeforeInactive;
        /** workflow name → stats */
        private Map<String, WorkflowTypeStats> byWorkflow;
        /** ngày → runs + duration */
        private Map<LocalDate, DailyWorkflowStats> dailyStats;
        private List<WorkflowUserActivity> topUsersByWorkflowTime;
        /** workflow name → failed run count */
        private Map<String, Long> failedRunsByWorkflow;
        /** Số lần & thời gian — luôn trả về (0 nếu chưa ai dùng) */
        private WorkflowToolStats regen;
        private WorkflowToolStats prepareRegen;
        private WorkflowToolStats extractLayers;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowToolStats {
        private String workflow;
        /** Trong khoảng báo cáo (today / week / from-date) */
        private Long runCount;
        private Long totalDurationMs;
        private Double avgDurationMs;
        private Long runsToday;
        private Long runsThisWeek;
        private Long runsThisMonth;
        private Long failedRuns;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowTypeStats {
        private String workflow;
        private Long runCount;
        private Long totalDurationMs;
        private Double avgDurationMs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyWorkflowStats {
        private Long runCount;
        private Long totalDurationMs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowUserActivity {
        private Long userId;
        private String email;
        /** Gói Feedback cao nhất trong kỳ (Free/Student/Pro). */
        private String planName;
        private Long runCount;
        private Long totalDurationMs;
        /** Tổng token AI trong kỳ. */
        private Long aiTokensUsed;
        /** Số ngày DISTINCT có dùng AI trong kỳ. */
        private Long activeDaysInPeriod;
        /** Số ngày không dùng AI từ lần cuối → hôm nay. */
        private Long daysInactiveUntilNow;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserActivityDTO {
        private Long userId;
        private String email;
        private String planName;
        private Long chatCount;
        private Long aiTokensUsed;
    }
}
