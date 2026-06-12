package com.willa.ai.backend.service;

public interface GoogleMeetService {
    /**
     * Tạo sự kiện Calendar kèm link Google Meet.
     * @throws IllegalStateException nếu chưa cấu hình credentials
     */
    String createMeetLink(String summary, String description);
}
