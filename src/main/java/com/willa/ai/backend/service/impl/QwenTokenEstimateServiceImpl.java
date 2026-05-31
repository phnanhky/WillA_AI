package com.willa.ai.backend.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.willa.ai.backend.config.QwenTokenEstimateProperties;
import com.willa.ai.backend.dto.response.QwenTokenEstimateResponse;
import com.willa.ai.backend.entity.Wallet;
import com.willa.ai.backend.exception.InsufficientTokenException;
import com.willa.ai.backend.service.QwenTokenEstimateService;
import com.willa.ai.backend.util.ImageDimensions;
import com.willa.ai.backend.util.QwenVisionTokenMath;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class QwenTokenEstimateServiceImpl implements QwenTokenEstimateService {

    private static final int AI_SERVER_MAX_EDGE = 1536;

    private final QwenTokenEstimateProperties props;

    @Override
    public QwenTokenEstimateResponse estimateTextChat(String userMessage) {
        var chat = props.getChat();
        int userText = QwenVisionTokenMath.textTokens(userMessage, props.getCharsPerToken());
        long input = chat.getSystemAndContextTokens() + userText;
        long output = withOutputMargin(chat.getOutputTokens());
        return buildResponse(input, output, 0);
    }

    @Override
    public QwenTokenEstimateResponse estimateFeedbackDesign(
            List<ImageEstimateInput> images,
            String userMessage) {
        if (images == null || images.isEmpty()) {
            return estimateTextChat(userMessage);
        }

        int userText = QwenVisionTokenMath.textTokens(userMessage, props.getCharsPerToken());
        long totalInput = userText;
        long totalOutput = 0;
        for (ImageEstimateInput image : images) {
            long[] io = estimateOneImageLocal(image, userText);
            totalInput += io[0];
            totalOutput += io[1];
        }
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
        long output = withOutputMargin(fb.getOutputTokensTypical());

        log.info(
                "Token ESTIMATE (local) [{}]: input={} (img={}, text={}, buffer={}), output={}, total={}, size={}x{}",
                image.filename(), input, imageTokens, textTokens, fb.getWalletBufferTokens(),
                output, input + output, wh[0], wh[1]);
        return new long[] { input, output };
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
        if (w <= AI_SERVER_MAX_EDGE && h <= AI_SERVER_MAX_EDGE) {
            return wh;
        }
        double scale = Math.min((double) AI_SERVER_MAX_EDGE / w, (double) AI_SERVER_MAX_EDGE / h);
        return new int[] {
                Math.max(1, (int) Math.floor(w * scale)),
                Math.max(1, (int) Math.floor(h * scale))
        };
    }

    private long withOutputMargin(long outputTokens) {
        if (outputTokens <= 0 || props.getSafetyMarginPercent() <= 0) {
            return outputTokens;
        }
        return outputTokens + (outputTokens * props.getSafetyMarginPercent() / 100);
    }

    private QwenTokenEstimateResponse buildResponse(long input, long output, int imageCount) {
        return QwenTokenEstimateResponse.builder()
                .inputTokens(input)
                .outputTokens(output)
                .totalTokens(input + output)
                .imageCount(imageCount)
                .build();
    }
}
