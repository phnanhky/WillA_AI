package com.willa.ai.backend.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileService {
    String uploadFile(MultipartFile file);

    /** Upload raw bytes (dùng khi upload song song với gọi AI). */
    String uploadBytes(byte[] data, String originalFilename, String contentType);

    byte[] downloadFile(String fileName);
}