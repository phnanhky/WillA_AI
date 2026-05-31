package com.willa.ai.backend.util;

import java.io.ByteArrayInputStream;

import javax.imageio.ImageIO;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ImageDimensions {

    private ImageDimensions() {}

    /** @return [width, height] hoặc [0,0] nếu không đọc được */
    public static int[] readWidthHeight(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return new int[] { 0, 0 };
        }
        try (var in = new ByteArrayInputStream(imageBytes)) {
            var image = ImageIO.read(in);
            if (image == null) {
                return new int[] { 1024, 1024 };
            }
            return new int[] { image.getWidth(), image.getHeight() };
        } catch (Exception e) {
            log.debug("Could not read image dimensions, using fallback 1024x1024: {}", e.getMessage());
            return new int[] { 1024, 1024 };
        }
    }
}
