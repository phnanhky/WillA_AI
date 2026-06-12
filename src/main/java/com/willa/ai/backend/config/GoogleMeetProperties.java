package com.willa.ai.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "google.meet")
@Getter
@Setter
public class GoogleMeetProperties {
    /** Bật tạo link Meet qua Google Calendar API */
    private boolean enabled = false;

    /** JSON service account (Calendar scope). Trống = dùng FIREBASE credentials path nếu có. */
    private String credentialsPath;

    /** Calendar ID (primary hoặc email calendar) */
    private String calendarId = "primary";

    /** Domain-wide delegation: email user Workspace để tạo Meet thay mặt */
    private String impersonateEmail;
}
