package com.willa.ai.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Hằng số tọa độ dùng chung với qwenv3vagenAI — xem docs/BOUNDING_BOX_COORDINATES.md.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.analysis")
public class AnalysisCoordinateProperties {

    /** Khớp {@code main.py} MAX_SIZE / thumbnail. */
    private int maxEdgePixels = 1536;

    /** Qwen grounding grid. */
    private int qwenGridMax = 1000;

    /** Giá trị ghi vào {@code analysis_data.coord_space} sau remap. */
    private String coordSpaceOutput = "source_pixel";
}
