package com.willa.ai.backend.util;

/**
 * Khớp pipeline qwenv3vagenAI {@code main.py}: {@code Image.thumbnail((1536, 1536), LANCZOS)}.
 * Box AI trả về theo byte ảnh sau bước này ({@code isz}); FE hiển thị ảnh upload/R2 (thường lớn hơn).
 */
public final class AiImageFrameUtil {

    /**
     * Cùng {@code app.analysis.max-edge-pixels} / AI {@code main.py} MAX_SIZE.
     * @see com.willa.ai.backend.config.AnalysisCoordinateProperties
     */
    public static final int MAX_EDGE = 1536;

    private AiImageFrameUtil() {
    }

    /**
     * Kích thước ảnh sau khi AI server thumbnail (fit inside MAX_EDGE×MAX_EDGE, giữ aspect).
     */
    public static int[] analysisFrameSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return new int[] { 0, 0 };
        }
        if (width <= MAX_EDGE && height <= MAX_EDGE) {
            return new int[] { width, height };
        }
        double scale = Math.min((double) MAX_EDGE / width, (double) MAX_EDGE / height);
        return new int[] {
                Math.max(1, (int) Math.floor(width * scale)),
                Math.max(1, (int) Math.floor(height * scale)),
        };
    }

    /** Scale pixel box từ khung phân tích sang ảnh gốc (upload / R2). */
    public static int[] scalePixelBoxToSource(
            int[] box,
            int analysisW,
            int analysisH,
            int sourceW,
            int sourceH) {
        if (box == null || box.length < 4 || analysisW <= 0 || analysisH <= 0 || sourceW <= 0 || sourceH <= 0) {
            return box;
        }
        if (analysisW == sourceW && analysisH == sourceH) {
            return box;
        }
        double sx = (double) sourceW / analysisW;
        double sy = (double) sourceH / analysisH;
        return new int[] {
                (int) Math.round(box[0] * sx),
                (int) Math.round(box[1] * sy),
                (int) Math.round(box[2] * sx),
                (int) Math.round(box[3] * sy),
        };
    }
}
