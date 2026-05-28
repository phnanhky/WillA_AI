package com.willa.ai.backend.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

/**
 * Giới hạn kích thước file người dùng upload lên server (trước khi gửi AI hoặc lưu R2).
 * Nén / resize cho R2 được xử lý riêng bởi {@link ImageUploadCompressor}.
 *
 * <ul>
 *   <li>Mỗi ảnh: 20MB</li>
 *   <li>PDF: 512MB</li>
 *   <li>CSV: 50MB</li>
 *   <li>Tổng một request (PDF / nhiều ảnh gói PRO): 512MB</li>
 * </ul>
 */
@Component
public class UploadSizeValidator {

    @Value("${app.upload.max-image-bytes:20971520}")
    private long maxImageBytes;

    @Value("${app.upload.max-pdf-bytes:536870912}")
    private long maxPdfBytes;

    @Value("${app.upload.max-csv-bytes:52428800}")
    private long maxCsvBytes;

    @Value("${app.upload.max-request-bytes:536870912}")
    private long maxRequestBytes;

    /** Validate một ảnh đơn (dùng cho upload R2, suggest-style, workspace…). */
    public void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return;
        }
        validateImageBytes(file.getSize(), file.getOriginalFilename());
    }

    public void validateImageBytes(long sizeBytes, String filename) {
        validateLimit(sizeBytes, maxImageBytes, filename);
    }

    /** Validate theo phần mở rộng của file (pdf/csv/psd/ảnh). */
    public void validateByType(long sizeBytes, String filename) {
        validateLimit(sizeBytes, limitForFilename(filename), filename);
    }

    /** Tổng dung lượng cả request (nhiều ảnh / PDF gói PRO). */
    public void validateRequestTotal(long totalBytes) {
        if (totalBytes > maxRequestBytes) {
            throw new IllegalArgumentException(
                    "Tổng dung lượng upload không được vượt quá "
                            + toMb(maxRequestBytes) + " MB");
        }
    }

    private long limitForFilename(String filename) {
        String lower = filename != null ? filename.toLowerCase(Locale.ROOT) : "";
        if (lower.endsWith(".pdf") || lower.endsWith(".psd")) {
            return maxPdfBytes;
        }
        if (lower.endsWith(".csv")) {
            return maxCsvBytes;
        }
        return maxImageBytes;
    }

    private void validateLimit(long sizeBytes, long limit, String filename) {
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("Cannot upload empty file");
        }
        if (sizeBytes > limit) {
            throw new IllegalArgumentException(
                    "File vượt quá giới hạn " + toMb(limit) + " MB"
                            + (filename != null && !filename.isBlank() ? " (" + filename + ")" : ""));
        }
    }

    private static long toMb(long bytes) {
        return bytes / (1024 * 1024);
    }
}
