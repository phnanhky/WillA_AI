package com.willa.ai.backend.controller;

import com.willa.ai.backend.dto.response.ApiResponse;
import com.willa.ai.backend.dto.response.WorkflowUsageReportResponse;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.service.WorkflowUsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/workflow-usage")
@RequiredArgsConstructor
@Tag(name = "Workflow Usage", description = "Track and report wall-clock time per product workflow")
@SecurityRequirement(name = "bearerAuth")
public class WorkflowUsageController {

    private final WorkflowUsageService workflowUsageService;
    private final UserRepository userRepository;

    @GetMapping("/me")
    @Operation(summary = "Báo cáo thời gian dùng workflow của tôi trong khoảng thời gian")
    public ResponseEntity<ApiResponse> getMyUsage(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "false") boolean includeLogs,
            Authentication authentication) {
        Long userId = resolveUserId(authentication);
        LocalDateTime from = start.atStartOfDay();
        LocalDateTime to = end.atTime(LocalTime.MAX);
        WorkflowUsageReportResponse report =
                workflowUsageService.getReportForUser(userId, from, to, includeLogs);
        return ok(report);
    }

    @GetMapping("/report")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: báo cáo thời gian workflow — 1 user, nhiều user, 1 ngày hoặc khoảng ngày")
    public ResponseEntity<ApiResponse> getReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String userIds,
            @RequestParam(defaultValue = "false") boolean includeLogs) {
        List<Long> ids = parseUserIds(userId, userIds);
        LocalDateTime from = start.atStartOfDay();
        LocalDateTime to = end.atTime(LocalTime.MAX);
        WorkflowUsageReportResponse report;
        if (ids == null || ids.isEmpty()) {
            report = workflowUsageService.getSystemReport(from, to, includeLogs);
        } else {
            report = workflowUsageService.getReport(ids, from, to, includeLogs);
        }
        return ok(report);
    }

    @GetMapping("/report/at")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: báo cáo tại một thời điểm (cả ngày của ngày đó)")
    public ResponseEntity<ApiResponse> getReportAtInstant(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String userIds,
            @RequestParam(defaultValue = "false") boolean includeLogs) {
        List<Long> ids = parseUserIds(userId, userIds);
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.atTime(LocalTime.MAX);
        WorkflowUsageReportResponse report;
        if (ids == null || ids.isEmpty()) {
            report = workflowUsageService.getSystemReport(from, to, includeLogs);
        } else {
            report = workflowUsageService.getReport(ids, from, to, includeLogs);
        }
        return ok(report);
    }

    /** null = toàn hệ thống (admin overview) */
    private List<Long> parseUserIds(Long userId, String userIdsCsv) {
        if (userIdsCsv != null && !userIdsCsv.isBlank()) {
            return Arrays.stream(userIdsCsv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .distinct()
                    .toList();
        }
        if (userId != null) {
            return List.of(userId);
        }
        return List.of();
    }

    private Long resolveUserId(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"))
                .getId();
    }

    private ResponseEntity<ApiResponse> ok(WorkflowUsageReportResponse report) {
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("Workflow usage report retrieved successfully")
                .data(report)
                .build());
    }
}
