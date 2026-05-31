package com.willa.ai.backend.util;

import java.util.regex.Pattern;

/**
 * Lọc PII và ký tự nguy hiểm trước khi đưa text vào persona / AI context.
 */
public final class PersonaTextSanitizer {

    private static final Pattern EMAIL = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE = Pattern.compile(
            "(\\+?\\d[\\d\\s().-]{7,}\\d)");
    private static final Pattern URL = Pattern.compile(
            "(https?://\\S+|www\\.\\S+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x1F\\x7F]");

    private PersonaTextSanitizer() {
    }

    public static String sanitizeLabel(String raw, int maxLen) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return null;
        }
        t = CONTROL_CHARS.matcher(t).replaceAll("");
        t = EMAIL.matcher(t).replaceAll("[redacted]");
        t = PHONE.matcher(t).replaceAll("[redacted]");
        t = URL.matcher(t).replaceAll("[redacted]");
        t = t.replaceAll("[<>\"'`\\\\]", "");
        if (t.length() > maxLen) {
            t = t.substring(0, maxLen).trim();
        }
        return t.isEmpty() ? null : t;
    }

    public static String sanitizeHint(String raw, int maxLen) {
        String s = sanitizeLabel(raw, maxLen);
        if (s == null) {
            return null;
        }
        return s.replaceAll("\\s+", " ");
    }
}
