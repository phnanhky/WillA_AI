package com.willa.ai.backend.service;

import com.willa.ai.backend.dto.request.PersonaSettingsRequest;
import com.willa.ai.backend.dto.response.PersonaResponse;

public interface PersonaService {

    /**
     * Persona của chính user đang đăng nhập (resolve qua email từ JWT).
     */
    PersonaResponse getMyPersona(String authenticatedEmail);

    PersonaResponse updateMySettings(String authenticatedEmail, PersonaSettingsRequest request);

    PersonaResponse refreshMyPersona(String authenticatedEmail, boolean enforceCooldown);

    /**
     * JSON đã sanitize để inject sang AI server — null nếu tắt / trống / lỗi.
     * Không throw ra ngoài luồng chat.
     */
    String getAiContextJsonForUser(Long userId);

    /** Gọi nội bộ sau mỗi lần phân tích design thành công — bỏ qua cooldown. */
    void refreshAfterAnalysis(Long userId);
}
