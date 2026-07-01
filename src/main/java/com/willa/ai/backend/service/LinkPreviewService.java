package com.willa.ai.backend.service;

import com.willa.ai.backend.dto.response.LinkPreviewResponse;

public interface LinkPreviewService {
    LinkPreviewResponse resolveTitle(String url);
}
