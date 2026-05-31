package com.willa.ai.backend.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;

/**
 * Rút tín hiệu design từ JSON phân tích đã lưu — không giữ issue text đầy đủ (tránh PII).
 */
public final class PersonaAnalysisParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PersonaAnalysisParser() {
    }

    @Getter
    public static final class DesignSignals {
        private final Map<String, Integer> categoryCounts = new LinkedHashMap<>();
        private final Map<String, Integer> severityCounts = new HashMap<>();
        private final List<String> focusHints = new ArrayList<>();
        private int parsedAnalyses;
    }

    public static DesignSignals aggregate(List<String> analysisJsonContents, int maxHintsPerAnalysis) {
        DesignSignals signals = new DesignSignals();
        if (analysisJsonContents == null) {
            return signals;
        }
        for (String content : analysisJsonContents) {
            if (content == null || content.isBlank()) {
                continue;
            }
            try {
                JsonNode root = MAPPER.readTree(content);
                if (!"analysis".equals(root.path("type").asText(""))) {
                    continue;
                }
                JsonNode data = root.path("analysis_data");
                if (!data.isObject()) {
                    continue;
                }
                signals.parsedAnalyses++;
                mergeSeverity(signals.severityCounts, data.path("ss"));
                mergeErrors(signals, data.path("e"), maxHintsPerAnalysis);
            } catch (Exception ignored) {
                // Bỏ qua bản ghi hỏng — không log content (bảo mật)
            }
        }
        return signals;
    }

    private static void mergeSeverity(Map<String, Integer> target, JsonNode ss) {
        if (!ss.isObject()) {
            return;
        }
        addCount(target, "minor", ss.path("minor").asInt(0));
        addCount(target, "major", ss.path("major").asInt(0));
        addCount(target, "critical", ss.path("critical").asInt(0));
    }

    private static void mergeErrors(DesignSignals signals, JsonNode errors, int maxHints) {
        if (!errors.isArray()) {
            return;
        }
        int hints = 0;
        for (JsonNode err : errors) {
            String category = normalizeCategory(err.path("g").asText(null));
            if (category != null) {
                signals.categoryCounts.merge(category, 1, Integer::sum);
            }
            String severity = err.path("s").asText(null);
            if (severity != null && !severity.isBlank()) {
                String key = severity.toLowerCase(Locale.ROOT);
                signals.severityCounts.merge(key, 1, Integer::sum);
            }
            if (hints < maxHints) {
                String hint = buildHint(category, severity);
                if (hint != null && !signals.focusHints.contains(hint)) {
                    signals.focusHints.add(hint);
                    hints++;
                }
            }
        }
    }

    private static String buildHint(String category, String severity) {
        if (category == null) {
            return null;
        }
        String friendly = category.replace('_', ' ');
        if (severity == null || severity.isBlank()) {
            return friendly;
        }
        return severity.toLowerCase(Locale.ROOT) + " " + friendly;
    }

    private static String normalizeCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static void addCount(Map<String, Integer> target, String key, int value) {
        if (value > 0) {
            target.merge(key, value, Integer::sum);
        }
    }

    public static List<String> topCategories(Map<String, Integer> counts, int limit) {
        return counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }
}
