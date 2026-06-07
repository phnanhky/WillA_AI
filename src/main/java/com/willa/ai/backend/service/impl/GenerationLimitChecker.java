package com.willa.ai.backend.service.impl;

import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.enums.WorkflowType;
import com.willa.ai.backend.repository.WorkflowUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GenerationLimitChecker {

    private final WorkflowUsageRepository workflowUsageRepository;

    public void checkLimit(User user, String planName) {
        if (planName == null) planName = "Free";
        planName = planName.toLowerCase().replace(" ", "");

        int limit = 0;
        if (planName.equals("free")) {
            limit = 0;
        } else if (planName.equals("student")) {
            limit = 10;
        } else if (planName.equals("pro")) {
            limit = 60;
        } else if (planName.equals("proplus")) {
            limit = 120;
        } else {
            limit = 0; // Default
        }

        if (limit == 0) {
            throw new RuntimeException("Gói " + planName + " không hỗ trợ tạo ảnh. Vui lòng nâng cấp gói để sử dụng tính năng này.");
        }

        LocalDateTime startOfMonth = YearMonth.now().atDay(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        List<Object[]> stats = workflowUsageRepository.countWorkflowsByUserSince(user.getId(), startOfMonth, now);
        long currentMonthGens = 0;
        for (Object[] row : stats) {
            WorkflowType type = (WorkflowType) row[0];
            if (type == WorkflowType.REGEN || type == WorkflowType.EXTRACT_LAYERS) {
                currentMonthGens += ((Number) row[1]).longValue();
            }
        }

        if (currentMonthGens >= limit) {
            throw new RuntimeException("Bạn đã đạt giới hạn tạo ảnh trong tháng của gói " + planName + " (" + limit + " lần).");
        }
    }
}
