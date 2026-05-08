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
    
    // Chi tiết người dùng
    private List<UserActivityDTO> topActiveUsers;
    private Map<LocalDate, Long> dailyChatCounts; // Số chat mỗi ngày
    
    // Chức năng được dùng nhiều
    private Map<String, Long> featureUsageByActionType;
    
    // Phân tích theo subscription plan
    private Map<String, Long> usersByPlan;
    private Map<String, Long> chatsByPlan;
    
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
