package com.willa.ai.backend.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileService {
    String uploadFile(MultipartFile file);

    /** Upload raw bytes (dùng khi upload song song với gọi AI). */
    String uploadBytes(byte[] data, String originalFilename, String contentType);

    /** Upload bytes tới key R2 cố định (không nén — dùng QR, file nhị phân). */
    String uploadRawBytes(byte[] data, String objectKey, String contentType);

    byte[] downloadFile(String fileName);

    String buildDownloadUrl(String objectKey);

    /** Upload tài liệu/ảnh gốc lên R2 — không nén, giữ nguyên file (expert booking, PDF, PSD…). */
    String uploadDocument(MultipartFile file);
}