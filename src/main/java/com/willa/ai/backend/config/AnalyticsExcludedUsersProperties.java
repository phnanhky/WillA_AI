package com.willa.ai.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tài khoản test / nội bộ — không tính vào admin analytics.
 * <p>
 * File cấu hình: {@code application.yaml} → {@code app.analytics.excluded-user-ids}<br>
 * Env: {@code ANALYTICS_EXCLUDED_USER_IDS=3,15,215}
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.analytics")
public class AnalyticsExcludedUsersProperties {

    /**
     * CSV user ids, ví dụ {@code 3,15,215}.
     * Mặc định khớp yaml; env {@code ANALYTICS_EXCLUDED_USER_IDS} ghi đè.
     */
    private String excludedUserIds = "3,15,215";

    private Set<Long> idSet = Set.of();

    @PostConstruct
    void parseIds() {
        idSet = parse(excludedUserIds);
    }

    public void setExcludedUserIds(String excludedUserIds) {
        this.excludedUserIds = excludedUserIds;
        this.idSet = parse(excludedUserIds);
    }

    public Set<Long> idSet() {
        return idSet;
    }

    public boolean isExcluded(Long userId) {
        return userId != null && idSet.contains(userId);
    }

    /** Param cho {@code NOT IN (:excludedUserIds)} — luôn ≥1 phần tử. */
    public Collection<Long> queryIds() {
        if (idSet.isEmpty()) {
            return List.of(-1L);
        }
        return idSet;
    }

    private static Set<Long> parse(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split("[,\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
