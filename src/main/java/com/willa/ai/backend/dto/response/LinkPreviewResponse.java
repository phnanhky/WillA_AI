package com.willa.ai.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkPreviewResponse {
    private String url;
    /** Tên file/trang (đã bỏ hậu tố Google). */
    private String title;
    /** Loại nguồn: Google Sheets, Google Docs, Google Drive, … */
    private String source;
    /** og:image thumbnail (nếu có). */
    private String imageUrl;
}
