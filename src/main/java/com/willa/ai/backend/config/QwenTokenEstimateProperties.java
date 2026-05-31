package com.willa.ai.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Ước lượng token Phase 3 trên BE — hiệu chỉnh từ log ACTUAL (không gọi AI server).
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.qwen.token-estimate")
public class QwenTokenEstimateProperties {

    private int patchFactor = 28;
    private long minPixels = 3_136L;
    private long maxPixels = 12_845_056L;
    private double charsPerToken = 3.2;
    /** Margin output (percent). */
    private int safetyMarginPercent = 2;

    private Feedback feedback = new Feedback();
    private Chat chat = new Chat();

    @Data
    public static class Feedback {
        /**
         * Text prompt Phase 3 (system + rules + grounding) — log ACTUAL text ≈ 3386 khi ảnh ~1247 vision tok.
         */
        private int basePromptTokens = 3_386;
        private int outputTokensTypical = 1_150;
        /**
         * Ảnh nhỏ (ít vision token) → DashScope bill text cao hơn (~3875 vs ~3386).
         * text += max(0, threshold - imageTokens) × factor
         */
        private int smallImageBoostThreshold = 1_300;
        private double smallImageBoostFactor = 0.86;
        /** Dư ~100 token so với ACTUAL để an toàn ví. */
        private int walletBufferTokens = 100;
    }

    @Data
    public static class Chat {
        private int systemAndContextTokens = 400;
        private int outputTokens = 900;
    }
}
