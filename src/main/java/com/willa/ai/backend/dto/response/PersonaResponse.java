package com.willa.ai.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Persona trả về cho client — không chứa email, phone, URL ảnh, hay chat thô.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonaResponse {

    private Boolean enabled;
    private String summary;
    private PersonaProfileView profile;
    private PersonaBehaviorView behavior;
    private PersonaDesignPatternsView designPatterns;
    private LocalDateTime updatedAt;
    /** Thời điểm sớm nhất user có thể gọi refresh thủ công (rate limit). */
    private LocalDateTime nextRefreshAllowedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PersonaProfileView {
        /** Nhãn nghề nghiệp đã sanitize (có thể null). */
        private String occupationLabel;
        /** free | student | pro */
        private String planTier;
        private Boolean isStudent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PersonaBehaviorView {
        private String primaryWorkflow;
        private Map<String, Long> workflowCounts30d;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PersonaDesignPatternsView {
        private int recentAnalysisCount;
        private List<String> topIssueCategories;
        private Map<String, Integer> severityMix;
        private List<String> focusHints;
    }
}
