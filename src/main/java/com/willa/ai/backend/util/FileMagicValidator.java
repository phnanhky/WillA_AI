package com.willa.ai.backend.util;

import java.util.Locale;

/**
 * Kiểm tra nội dung file bằng magic bytes — không tin đuôi / Content-Type từ client
 * (vd. đổi {@code .txt} → {@code .jpg}).
 */
public final class FileMagicValidator {

    private FileMagicValidator() {}

    public enum Kind {
        JPEG,
        PNG,
        GIF,
        WEBP,
        BMP,
        PDF,
        PSD,
        UNKNOWN
    }

    public static Kind detect(byte[] data) {
        if (data == null || data.length < 4) {
            return Kind.UNKNOWN;
        }
        // JPEG
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return Kind.JPEG;
        }
        // PNG
        if (data.length >= 8
                && (data[0] & 0xFF) == 0x89
                && data[1] == 0x50
                && data[2] == 0x4E
                && data[3] == 0x47
                && data[4] == 0x0D
                && data[5] == 0x0A
                && data[6] == 0x1A
                && data[7] == 0x0A) {
            return Kind.PNG;
        }
        // GIF
        if (data[0] == 'G' && data[1] == 'I' && data[2] == 'F'
                && data[3] == '8' && (data[4] == '7' || data[4] == '9') && data[5] == 'a') {
            return Kind.GIF;
        }
        // BMP
        if (data[0] == 'B' && data[1] == 'M') {
            return Kind.BMP;
        }
        // PDF
        if (data[0] == '%' && data[1] == 'P' && data[2] == 'D' && data[3] == 'F') {
            return Kind.PDF;
        }
        // PSD
        if (data[0] == '8' && data[1] == 'B' && data[2] == 'P' && data[3] == 'S') {
            return Kind.PSD;
        }
        // WEBP: RIFF....WEBP
        if (data.length >= 12
                && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return Kind.WEBP;
        }
        return Kind.UNKNOWN;
    }

    public static boolean isImage(byte[] data) {
        Kind k = detect(data);
        return k == Kind.JPEG || k == Kind.PNG || k == Kind.GIF || k == Kind.WEBP || k == Kind.BMP;
    }

    public static boolean isPdf(byte[] data) {
        return detect(data) == Kind.PDF;
    }

    public static boolean isPsd(byte[] data) {
        return detect(data) == Kind.PSD;
    }

    public static String mimeFor(Kind kind) {
        return switch (kind) {
            case JPEG -> "image/jpeg";
            case PNG -> "image/png";
            case GIF -> "image/gif";
            case WEBP -> "image/webp";
            case BMP -> "image/bmp";
            case PDF -> "application/pdf";
            case PSD -> "image/vnd.adobe.photoshop";
            case UNKNOWN -> "application/octet-stream";
        };
    }

    public static String extensionFor(Kind kind) {
        return switch (kind) {
            case JPEG -> ".jpg";
            case PNG -> ".png";
            case GIF -> ".gif";
            case WEBP -> ".webp";
            case BMP -> ".bmp";
            case PDF -> ".pdf";
            case PSD -> ".psd";
            case UNKNOWN -> "";
        };
    }

    /** Ảnh thường (không PDF/PSD). */
    public static void requireImage(byte[] data, String filename) {
        if (!isImage(data)) {
            throw new IllegalArgumentException(rejectMessage(filename, "ảnh (JPEG/PNG/GIF/WEBP/BMP)"));
        }
    }

    /** Chat upload: ảnh hoặc PDF/PSD theo magic bytes. */
    public static Kind requireChatUpload(byte[] data, String filename) {
        Kind kind = detect(data);
        if (kind == Kind.UNKNOWN) {
            throw new IllegalArgumentException(rejectMessage(
                    filename, "ảnh (JPEG/PNG/GIF/WEBP/BMP), PDF hoặc PSD"));
        }
        return kind;
    }

    private static String rejectMessage(String filename, String allowed) {
        String name = filename != null && !filename.isBlank() ? filename : "file";
        return "File không hợp lệ hoặc giả mạo đuôi (" + name
                + "). Chỉ chấp nhận " + allowed + " thật — không nhận file đổi đuôi.";
    }

    public static boolean filenameLooksLikeImage(String filename) {
        if (filename == null) {
            return false;
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".bmp");
    }
}
