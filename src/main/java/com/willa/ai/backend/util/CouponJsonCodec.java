package com.willa.ai.backend.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.willa.ai.backend.dto.CouponPlanTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CouponJsonCodec {

    private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() {};
    private static final TypeReference<List<CouponPlanTarget>> PLAN_LIST = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public List<Long> decodeUserIds(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<Long> ids = objectMapper.readValue(json, LONG_LIST);
            return ids != null ? ids : Collections.emptyList();
        } catch (Exception e) {
            throw new IllegalArgumentException("Danh sách user không hợp lệ");
        }
    }

    public String encodeUserIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (Exception e) {
            throw new IllegalArgumentException("Không lưu được danh sách user");
        }
    }

    public List<CouponPlanTarget> decodePlans(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<CouponPlanTarget> plans = objectMapper.readValue(json, PLAN_LIST);
            return plans != null ? plans : Collections.emptyList();
        } catch (Exception e) {
            throw new IllegalArgumentException("Danh sách gói không hợp lệ");
        }
    }

    public String encodePlans(List<CouponPlanTarget> plans) {
        if (plans == null || plans.isEmpty()) {
            return null;
        }
        try {
            List<CouponPlanTarget> normalized = plans.stream()
                    .filter(p -> p.getPlanId() != null && p.getPlanType() != null && !p.getPlanType().isBlank())
                    .map(p -> CouponPlanTarget.builder()
                            .planType(p.getPlanType().trim().toUpperCase())
                            .planId(p.getPlanId())
                            .build())
                    .toList();
            if (normalized.isEmpty()) {
                return null;
            }
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            throw new IllegalArgumentException("Không lưu được danh sách gói");
        }
    }
}
