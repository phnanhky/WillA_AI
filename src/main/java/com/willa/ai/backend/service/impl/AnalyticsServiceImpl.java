package com.willa.ai.backend.service.impl;

import com.willa.ai.backend.dto.response.AnalyticsResponse;
import com.willa.ai.backend.dto.response.AnalyticsResponse.UserActivityDTO;
import com.willa.ai.backend.repository.AnalyticsRepository;
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
            .build();
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
