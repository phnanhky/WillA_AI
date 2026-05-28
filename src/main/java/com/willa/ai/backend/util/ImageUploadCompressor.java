package com.willa.ai.backend.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

/**
 * Chỉ dùng khi lưu lên R2 ({@link com.willa.ai.backend.service.impl.FileServiceImpl}).
 * Ảnh gửi sang AI server giữ nguyên bytes gốc (trong giới hạn upload 20MB của user).
 */
@Component
public class ImageUploadCompressor {

    private static final String JPEG = "image/jpeg";
    private static final float[] QUALITIES = {0.88f, 0.78f, 0.68f, 0.58f, 0.48f, 0.38f, 0.28f};
    private static final int[] MAX_DIMENSIONS = {4096, 3072, 2560, 2048, 1536, 1280, 1024, 800, 640};

    @Value("${cloudflare.r2.max-upload-bytes:3145728}")
    private long maxUploadBytes;

    public record PreparedUpload(byte[] data, String contentType, String extension) {}

    public PreparedUpload prepare(byte[] data, String contentType, String originalFilename) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Cannot upload empty file");
        }
        if (!isImage(contentType, originalFilename)) {
            if (data.length > maxUploadBytes) {
                throw new IllegalArgumentException(
                        "File must be under " + (maxUploadBytes / (1024 * 1024)) + " MB");
            }
            return unchanged(data, contentType, originalFilename);
        }
        if (data.length <= maxUploadBytes) {
            return unchanged(data, contentType, originalFilename);
        }
        try {
            return compressImage(data, originalFilename);
        } catch (IOException e) {
            throw new RuntimeException("Failed to compress image for upload: " + e.getMessage(), e);
        }
    }

    private PreparedUpload compressImage(byte[] data, String originalFilename) throws IOException {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(data));
        if (source == null) {
            throw new IllegalArgumentException("Unsupported or corrupted image file");
        }
        BufferedImage rgb = toRgb(source);

        for (int maxDim : MAX_DIMENSIONS) {
            BufferedImage scaled = scaleToMax(rgb, maxDim);
            for (float quality : QUALITIES) {
                byte[] jpeg = writeJpeg(scaled, quality);
                if (jpeg.length <= maxUploadBytes) {
                    return new PreparedUpload(jpeg, JPEG, ".jpg");
                }
            }
        }

        throw new IllegalArgumentException(
                "Image is too large; could not compress under "
                        + (maxUploadBytes / (1024 * 1024))
                        + " MB. Try a smaller image.");
    }

    private static PreparedUpload unchanged(byte[] data, String contentType, String originalFilename) {
        String ext = extensionFrom(contentType, originalFilename);
        String resolvedType = contentType != null && !contentType.isBlank()
                ? contentType
                : "application/octet-stream";
        return new PreparedUpload(data, resolvedType, ext);
    }

    private static boolean isImage(String contentType, String filename) {
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return true;
        }
        if (filename == null) {
            return false;
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".bmp");
    }

    private static String extensionFrom(String contentType, String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf('.'));
        }
        if (contentType == null) {
            return "";
        }
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> "";
        };
    }

    private static BufferedImage toRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, source.getWidth(), source.getHeight());
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return rgb;
    }

    private static BufferedImage scaleToMax(BufferedImage img, int maxDim) {
        int w = img.getWidth();
        int h = img.getHeight();
        if (w <= maxDim && h <= maxDim) {
            return img;
        }
        double scale = Math.min((double) maxDim / w, (double) maxDim / h);
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(img, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    private static byte[] writeJpeg(BufferedImage image, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG writer available");
        }
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
