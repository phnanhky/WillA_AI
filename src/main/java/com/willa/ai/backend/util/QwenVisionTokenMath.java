package com.willa.ai.backend.util;

/**
 * Công thức vision token Qwen-VL: resize ảnh theo patch, token ảnh ≈ (H×W) / patch² + 2.
 */
public final class QwenVisionTokenMath {

    private QwenVisionTokenMath() {}

    public static int textTokens(String text, double charsPerToken) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / Math.max(charsPerToken, 0.1));
    }

    public static int imageTokens(int width, int height, long minPixels, long maxPixels, int patchFactor) {
        if (width <= 0 || height <= 0) {
            return 0;
        }
        int[] resized = smartResize(width, height, minPixels, maxPixels, patchFactor);
        long pixels = (long) resized[0] * resized[1];
        int patchArea = patchFactor * patchFactor;
        return (int) (pixels / patchArea) + 2;
    }

    /** Token ảnh billable DashScope — grid (w×h)/patch², không +2. */
    public static int billableImageTokens(int width, int height, long minPixels, long maxPixels, int patchFactor) {
        if (width <= 0 || height <= 0) {
            return 0;
        }
        int[] resized = smartResize(width, height, minPixels, maxPixels, patchFactor);
        long pixels = (long) resized[0] * resized[1];
        int patchArea = patchFactor * patchFactor;
        return Math.max(1, (int) (pixels / patchArea));
    }

    public static long applySafetyMargin(long tokens, int marginPercent) {
        if (marginPercent <= 0) {
            return tokens;
        }
        return tokens + (tokens * marginPercent / 100);
    }

    /** Giữ tỷ lệ, làm tròn kích thước bội số của patchFactor. */
    static int[] smartResize(int width, int height, long minPixels, long maxPixels, int patchFactor) {
        double aspect = (double) width / height;
        long pixels = (long) width * height;

        if (pixels > maxPixels) {
            double scale = Math.sqrt((double) maxPixels / pixels);
            width = Math.max(patchFactor, roundToFactor((int) (width * scale), patchFactor));
            height = Math.max(patchFactor, roundToFactor((int) (height * scale), patchFactor));
        } else if (pixels < minPixels) {
            double scale = Math.sqrt((double) minPixels / pixels);
            width = roundToFactor((int) (width * scale), patchFactor);
            height = roundToFactor((int) (height * scale), patchFactor);
        } else {
            width = roundToFactor(width, patchFactor);
            height = roundToFactor(height, patchFactor);
        }

        if (width < 1) {
            width = patchFactor;
        }
        if (height < 1) {
            height = patchFactor;
        }
        if (aspect > 0 && Math.abs((double) width / height - aspect) > 0.01) {
            height = Math.max(patchFactor, roundToFactor((int) (width / aspect), patchFactor));
        }
        return new int[] { width, height };
    }

    private static int roundToFactor(int value, int factor) {
        return Math.max(factor, (value / factor) * factor);
    }
}
