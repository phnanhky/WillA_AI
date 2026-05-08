package com.willa.ai.backend.service;

import com.willa.ai.backend.dto.response.AnalyticsResponse;
import com.willa.ai.backend.dto.response.AnalyticsResponse.UserActivityDTO;
import java.time.LocalDate;

public interface AnalyticsService {
    
    /**
     * Lấy analytics từ ngày bắt đầu đến nay
     */
    AnalyticsResponse getAnalytics(LocalDate startDate);
    
    /**
     * Lấy analytics cho ngày hôm nay
     */
    AnalyticsResponse getTodayAnalytics();
    
    /**
     * Lấy analytics cho tuần này
     */
    AnalyticsResponse getThisWeekAnalytics();
}
