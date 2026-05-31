package com.willa.ai.backend.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.willa.ai.backend.client.AiServerClient;
import com.willa.ai.backend.config.QwenTokenEstimateProperties;
import com.willa.ai.backend.dto.response.QwenTokenEstimateResponse;
import com.willa.ai.backend.entity.Wallet;
import com.willa.ai.backend.exception.InsufficientTokenException;
import com.willa.ai.backend.service.QwenTokenEstimateService;
import com.willa.ai.backend.util.AiImageFrameUtil;
import com.willa.ai.backend.util.ImageDimensions;
import com.willa.ai.backend.util.QwenVisionTokenMath;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class QwenTokenEstimateServiceImpl implements QwenTokenEstimateService {

    private final QwenTokenEstimateProperties props;
    private final AiServerClient aiServerClient;

    @Override
    public QwenTokenEstimateResponse estimateTextChat(
            String userMessage,
            String personaContextJson,
            QwenTokenEstimateService.TextChatEstimateContext context) {
        if (context != null && context.sessionHasImageContext()) {
            return estimateSessionRoutingChat(userMessage, personaContextJson, context);
        }
        return estimatePlainTextChat(userMessage, personaContextJson);
    }

    /** Không có ảnh trong session AI — system prompt ngắn, reply text. */
    private QwenTokenEstimateResponse estimatePlainTextChat(String userMessage, String personaContextJson) {
        var chat = props.getChat();
        int persona = textTokens(personaContextJson);
        int userText = textTokens(userMessage);
        long input = chat.getPlainSystemTokens() + persona + userText + chat.getWalletBufferTokens();
        long output = withOutputMargin(chat.getPlainOutputTokens());
        log.info(
                "Token ESTIMATE (text-plain): input={}, output={}, total={}",
                input, output, withTotalSafetyMargin(input + output));
        return buildResponse(input, output, 0);
    }

    /**
     * Tin nhắn chữ sau khi đã phân tích ảnh — AI unified /chat với intent classifier + history.
     */
    private QwenTokenEstimateResponse estimateSessionRoutingChat(
            String userMessage,
            String personaContextJson,
            QwenTokenEstimateService.TextChatEstimateContext context) {
        var chat = props.getChat();
        int persona = textTokens(personaContextJson);
        int userText = textTokens(userMessage);
        long input = chat.getRoutingSystemTokens()
                + chat.getRoutingSessionStateTokens()
                + persona
                + userText
                + Math.max(0, context.historyTextTokens())
                + Math.max(0, context.analysisContextTextTokens())
                + chat.getWalletBufferTokens();
        long output = withOutputMargin(chat.getRoutingOutputTokens());
        log.info(
                "Token ESTIMATE (text-session-routing): input={} (history={}, analysisCtx={}), output={}, total={}",
                input, context.historyTextTokens(), context.analysisContextTextTokens(),
                output, withTotalSafetyMargin(input + output));
        return buildResponse(input, output, 0);
    }

    private int textTokens(String text) {
        return QwenVisionTokenMath.textTokens(text != null ? text : "", props.getCharsPerToken());
    }

    @Override
    public QwenTokenEstimateResponse estimateFeedbackDesign(
            List<ImageEstimateInput> images,
            String userMessage,
            String personaContextJson) {
        if (images == null || images.isEmpty()) {
            return estimateTextChat(
                    userMessage, personaContextJson, QwenTokenEstimateService.TextChatEstimateContext.plain());
        }

        if (props.isUseAiServer()) {
            try {
                return estimateFeedbackViaAiServer(images, userMessage, personaContextJson);
            } catch (Exception e) {
                log.warn("AI server token estimate failed, using local formula: {}", e.getMessage());
            }
        }

        return estimateFeedbackLocal(images, userMessage, personaContextJson);
    }

    private QwenTokenEstimateResponse estimateFeedbackLocal(
            List<ImageEstimateInput> images,
            String userMessage,
            String personaContextJson) {
        int extra = QwenVisionTokenMath.textTokens(
                personaContextJson != null ? personaContextJson : "",
                props.getCharsPerToken());
        int userText = QwenVisionTokenMath.textTokens(userMessage, props.getCharsPerToken());
        long totalInput = 0;
        long totalOutput = 0;
        for (ImageEstimateInput image : images) {
            long[] io = estimateOneImageLocal(image, userText + extra);
            totalInput += io[0];
            totalOutput += io[1];
        }
        return buildResponse(totalInput, totalOutput, images.size());
    }

    private QwenTokenEstimateResponse estimateFeedbackViaAiServer(
            List<ImageEstimateInput> images,
            String userMessage,
            String personaContextJson) {
        long totalInput = 0;
        long totalOutput = 0;
        for (ImageEstimateInput image : images) {
            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("file", AiServerClient.toFileResource(image.bytes(), image.filename()));
            if (userMessage != null && !userMessage.isBlank()) {
                form.add("message", userMessage);
            }
            if (personaContextJson != null && !personaContextJson.isBlank()) {
                form.add("persona_context", personaContextJson);
            }
            JsonNode node = aiServerClient.estimate(form);
            totalInput += node.path("input_tokens").asLong(0);
            totalOutput += node.path("output_tokens").asLong(0);
        }
        log.info(
                "Token ESTIMATE (ai-server): input={}, output={}, total={}, images={}",
                totalInput, totalOutput, totalInput + totalOutput, images.size());
        return buildResponse(totalInput, totalOutput, images.size());
    }

    @Override
    public void requireSufficientBalance(Wallet wallet, long requiredTokens) {
        if (wallet == null) {
            throw new IllegalArgumentException("Wallet not found");
        }
        long available = wallet.getTokenBalance() != null ? wallet.getTokenBalance() : 0L;
        if (available < requiredTokens) {
            throw new InsufficientTokenException(requiredTokens, available);
        }
    }

    /**
     * Phase 3 billable ≈ vision(image) + text(prompt) + buffer.
     * Text scale: basePrompt + boost khi vision token thấp (calibrated từ log ACTUAL).
     */
    private long[] estimateOneImageLocal(ImageEstimateInput image, int userTextTokens) {
        var fb = props.getFeedback();
        int[] wh = dimensionsAfterUploadResize(image.bytes());
        int imageTokens = QwenVisionTokenMath.billableImageTokens(
                wh[0], wh[1], props.getMinPixels(), props.getMaxPixels(), props.getPatchFactor());

        long textTokens = estimatePhase3TextTokens(imageTokens, userTextTokens, fb);
        long input = imageTokens + textTokens + fb.getWalletBufferTokens();
        long output = estimateOutputTokens(imageTokens, fb);

        log.info(
                "Token ESTIMATE (local) [{}]: input={} (img={}, text={}, buffer={}), output={}, total={}, size={}x{}",
                image.filename(), input, imageTokens, textTokens, fb.getWalletBufferTokens(),
                output, withTotalSafetyMargin(input + output), wh[0], wh[1]);
        return new long[] { input, output };
    }

    /**
     * Output ACTUAL dao động (JSON ngắn ~840 vs dài ~1286). Điều chỉnh theo vision token:
     * ảnh nhỏ → ít chi tiết → thường ít lỗi trong JSON.
     */
    private long estimateOutputTokens(int imageTokens, QwenTokenEstimateProperties.Feedback fb) {
        int pivot = fb.getOutputImagePivotTokens();
        int adjust = 0;
        int cap = fb.getOutputAdjustMax();
        if (imageTokens < pivot && fb.getOutputSmallImageAdjustDivisor() > 0) {
            adjust = -Math.min(cap, (int) Math.round((pivot - imageTokens) / fb.getOutputSmallImageAdjustDivisor()));
        } else if (imageTokens > pivot && fb.getOutputLargeImageAdjustFactor() > 0) {
            adjust = Math.min(cap, (int) Math.round((imageTokens - pivot) * fb.getOutputLargeImageAdjustFactor()));
        }
        long base = fb.getOutputTokensTypical() + adjust;
        long clamped = Math.max(fb.getOutputTokensMin(), Math.min(fb.getOutputTokensMax(), base));
        return withOutputMargin(clamped);
    }

    private long withTotalSafetyMargin(long subtotal) {
        double pct = props.getTotalSafetyMarginPercent();
        if (subtotal <= 0 || pct <= 0) {
            return subtotal;
        }
        return subtotal + Math.round(subtotal * pct / 100.0);
    }

    private long estimatePhase3TextTokens(
            int imageTokens, int userTextTokens, QwenTokenEstimateProperties.Feedback fb) {
        long text = fb.getBasePromptTokens() + userTextTokens;
        int headroom = fb.getSmallImageBoostThreshold() - imageTokens;
        if (headroom > 0 && fb.getSmallImageBoostFactor() > 0) {
            text += Math.round(headroom * fb.getSmallImageBoostFactor());
        }
        return text;
    }

    private int[] dimensionsAfterUploadResize(byte[] imageBytes) {
        int[] wh = ImageDimensions.readWidthHeight(imageBytes);
        int w = wh[0];
        int h = wh[1];
        if (w <= 0 || h <= 0) {
            return wh;
        }
        return AiImageFrameUtil.analysisFrameSize(w, h);
    }

    private long withOutputMargin(long outputTokens) {
        if (outputTokens <= 0 || props.getSafetyMarginPercent() <= 0) {
            return outputTokens;
        }
        return outputTokens + (outputTokens * props.getSafetyMarginPercent() / 100);
    }

    private QwenTokenEstimateResponse buildResponse(long input, long output, int imageCount) {
        long total = withTotalSafetyMargin(input + output);
        return QwenTokenEstimateResponse.builder()
                .inputTokens(input)
                .outputTokens(output)
                .totalTokens(total)
                .imageCount(imageCount)
                .build();
    }
}
