package com.willa.ai.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Ước lượng token Phase 3 — đồng bộ qwenv3vagenAI/backend/token_estimate.py.
 * @see docs/TOKEN_ESTIMATE.md
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.qwen.token-estimate")
public class QwenTokenEstimateProperties {

    /** true = gọi POST ai-server/estimate (cùng công thức Python); false = Java local. */
    private boolean useAiServer = false;

    private int patchFactor = 28;
    private long minPixels = 3_136L;
    private long maxPixels = 12_845_056L;
    private double charsPerToken = 3.2;
    /** Margin trên output ước lượng (percent). */
    private int safetyMarginPercent = 2;
    /** Margin trên tổng input+output (percent) — giữ estimate cao hơn ACTUAL ~1–2%. */
    private double totalSafetyMarginPercent = 1.5;

    private Feedback feedback = new Feedback();
    private Chat chat = new Chat();

    @Data
    public static class Feedback {
        /**
         * Text prompt Phase 3 (system + rules + grounding) — log ACTUAL text ≈ 3386 khi ảnh ~1247 vision tok.
         */
        private int basePromptTokens = 2_650;
        /** Output JSON Phase 3 trung vị; điều chỉnh theo vision token (ảnh nhỏ → JSON ngắn hơn). */
        private int outputTokensTypical = 1_100;
        private int outputTokensMin = 860;
        private int outputTokensMax = 1_320;
        /** Pivot vision token: dưới pivot giảm output ước, trên pivot tăng nhẹ. */
        private int outputImagePivotTokens = 1_100;
        private double outputSmallImageAdjustDivisor = 1.5;
        private double outputLargeImageAdjustFactor = 0.4;
        private int outputAdjustMax = 220;
        /**
         * Ảnh nhỏ (ít vision token) → DashScope bill text cao hơn.
         * text += max(0, threshold - imageTokens) × factor
         */
        private int smallImageBoostThreshold = 1_500;
        private double smallImageBoostFactor = 0.50;
        private int walletBufferTokens = 100;
    }

    @Data
    public static class Chat {
        /** Chat thuần — session chưa có ảnh phân tích trên AI (main.py nhánh cuối). */
        private int plainSystemTokens = 120;
        private int plainOutputTokens = 550;
        /**
         * Follow-up chữ trong session đã có ảnh (AI: chat_json intent + history + errors context).
         */
        private int routingSystemTokens = 1_180;
        private int routingSessionStateTokens = 120;
        private int routingOutputTokens = 480;
        private int historyMaxTurns = 10;
        /** Cap history (AI chỉ gửi ~10 turn; tránh đếm trùng tin nhắn dài). */
        private int routingHistoryCapTokens = 450;
        private int analysisErrorsBaseTokens = 160;
        private int analysisErrorsPerErrorTokens = 42;
        private int walletBufferTokens = 60;
    }
}
