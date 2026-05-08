package com.willa.ai.backend.controller;

import com.willa.ai.backend.dto.response.AnalyticsResponse;
import com.willa.ai.backend.dto.response.ApiResponse;
import com.willa.ai.backend.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Platform Analytics & Insights")
@SecurityRequirement(name = "bearerAuth")
public class AnalyticsController {
    
    private final AnalyticsService analyticsService;
    
    @GetMapping("/today")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lấy analytics hôm nay")
    public ResponseEntity<ApiResponse> getTodayAnalytics() {
        AnalyticsResponse analytics = analyticsService.getTodayAnalytics();
        return ResponseEntity.ok(ApiResponse.builder()
            .status(true)
            .message("Today analytics retrieved successfully")
            .data(analytics)
            .build());
    }
    
    @GetMapping("/this-week")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lấy analytics 7 ngày gần nhất")
    public ResponseEntity<ApiResponse> getThisWeekAnalytics() {
        AnalyticsResponse analytics = analyticsService.getThisWeekAnalytics();
        return ResponseEntity.ok(ApiResponse.builder()
            .status(true)
            .message("This week analytics retrieved successfully")
            .data(analytics)
            .build());
    }
    
    @GetMapping("/from-date")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lấy analytics từ một ngày cụ thể đến nay")
    public ResponseEntity<ApiResponse> getAnalyticsFromDate(
            @Parameter(description = "Ngày bắt đầu (yyyy-MM-dd)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate) {
        AnalyticsResponse analytics = analyticsService.getAnalytics(startDate);
        return ResponseEntity.ok(ApiResponse.builder()
            .status(true)
            .message("Analytics retrieved successfully")
            .data(analytics)
            .build());
    }
}
