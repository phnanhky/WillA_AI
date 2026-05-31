package com.willa.ai.backend.service;

import java.util.List;

import com.willa.ai.backend.dto.response.QwenTokenEstimateResponse;
import com.willa.ai.backend.entity.Wallet;

public interface QwenTokenEstimateService {

    record ImageEstimateInput(byte[] bytes, String filename) {}

    /**
     * Text chat (không upload ảnh lần này).
     * {@code context.sessionHasImageContext()} = true khi session đã có phân tích → AI gọi intent routing (khác chat thuần).
     */
    QwenTokenEstimateResponse estimateTextChat(
            String userMessage,
            String personaContextJson,
            TextChatEstimateContext context);

    record TextChatEstimateContext(
            boolean sessionHasImageContext,
            int historyTextTokens,
            int analysisContextTextTokens) {
        public static TextChatEstimateContext plain() {
            return new TextChatEstimateContext(false, 0, 0);
        }
    }

    /**
     * Feedback Design: mỗi ảnh (kể cả từng trang PDF) ≈ nhiều lần gọi Qwen trên AI server.
     */
    /**
     * @param personaContextJson JSON persona gửi kèm /chat (có thể null/blank).
     */
    QwenTokenEstimateResponse estimateFeedbackDesign(
            List<ImageEstimateInput> images,
            String userMessage,
            String personaContextJson);

    void requireSufficientBalance(Wallet wallet, long requiredTokens);
}
