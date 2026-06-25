package com.willa.ai.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowUsageReportResponse {

    private LocalDateTime from;
    private LocalDateTime to;
    /** Users included in this report */
    private List<Long> userIds;
    private long totalRuns;
    private long totalDurationMs;
    /** Distinct users with workflow activity in range (system report) */
    private Long activeUsers;
    private long failedRuns;
    private long successfulRuns;
    /** Aggregated by workflow type across all selected users */
    private List<WorkflowUsageSummaryItem> byWorkflow;
    /** Per-user totals (populated when multiple users or admin detail view) */
    private List<UserWorkflowUsageSummary> byUser;
    /** Optional detailed log rows */
    private List<WorkflowUsageLogItem> logs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowUsageSummaryItem {
        private String workflow;
        private long runCount;
        private long totalDurationMs;
        private double avgDurationMs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserWorkflowUsageSummary {
        private Long userId;
        private String email;
        private long runCount;
        private long totalDurationMs;
        /** workflow name → totalDurationMs */
        private Map<String, Long> durationMsByWorkflow;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowUsageLogItem {
        private Long id;
        private Long userId;
        private String workflow;
        private Long chatSessionId;
        private LocalDateTime startedAt;
        private LocalDateTime endedAt;
        private long durationMs;
        private String status;
    }
}
