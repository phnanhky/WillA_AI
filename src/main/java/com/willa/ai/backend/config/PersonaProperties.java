package com.willa.ai.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.persona")
public class PersonaProperties {

    /** Master switch — tắt toàn bộ inject persona sang AI server. */
    private boolean enabled = true;

    /** Số lần phân tích gần nhất dùng để suy luận gu design. */
    private int maxRecentAnalyses = 3;

    /** Giới hạn ký tự JSON gửi sang AI (chống prompt bloat / injection payload lớn). */
    private int maxAiContextChars = 4096;

    /** Cooldown giữa các lần user gọi refresh thủ công (giây). */
    private int refreshCooldownSeconds = 300;

    /** Cửa sổ thống kê hành vi (ngày). */
    private int behaviorWindowDays = 30;

    /** Độ dài tối đa occupation / text hiển thị. */
    private int maxTextFieldLength = 120;

    /** Số category lỗi design trả về trong persona. */
    private int maxTopCategories = 5;
}
