package com.willa.ai.backend.service.impl;

import com.willa.ai.backend.service.FileService;
import com.willa.ai.backend.util.ImageUploadCompressor;
import com.willa.ai.backend.util.UploadSizeValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final S3Client s3Client;
    private final ImageUploadCompressor imageUploadCompressor;
    private final UploadSizeValidator uploadSizeValidator;

    @Value("${cloudflare.r2.bucket}")
    private String bucketName;

    @Value("${app.baseUrl:http://localhost:8080}")
    private String appBaseUrl;

    @Override
    public String uploadFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Cannot upload empty file");
        }
        try {
            return uploadBytes(
                    file.getBytes(),
                    file.getOriginalFilename(),
                    file.getContentType());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file input stream", e);
        }
    }

    @Override
    public String uploadBytes(byte[] data, String originalFilename, String contentType) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Cannot upload empty file");
        }
        uploadSizeValidator.validateImageBytes(data.length, originalFilename);
        ImageUploadCompressor.PreparedUpload prepared =
                imageUploadCompressor.prepare(data, contentType, originalFilename);
        String uniqueFileName = UUID.randomUUID().toString() + prepared.extension();
        return putObject(uniqueFileName, prepared.data(), prepared.contentType());
    }

    @Override
    public String uploadRawBytes(byte[] data, String objectKey, String contentType) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Cannot upload empty file");
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("Object key is required");
        }
        return putObject(objectKey, data, contentType != null ? contentType : "application/octet-stream");
    }

    private String putObject(String objectKey, byte[] data, String contentType) {
        try {
            if ("dummy".equals(bucketName) || bucketName == null || bucketName.isEmpty()) {
                java.nio.file.Path uploadDir = java.nio.file.Paths.get("uploads");
                if (!java.nio.file.Files.exists(uploadDir)) {
                    java.nio.file.Files.createDirectories(uploadDir);
                }
                String safeKey = objectKey.replace("/", "_");
                java.nio.file.Path filePath = uploadDir.resolve(safeKey);
                java.nio.file.Files.write(filePath, data);
                return buildDownloadUrl(safeKey);
            }

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(data));
            return buildDownloadUrl(objectKey);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to Cloudflare R2", e);
        }
    }

    @Override
    public String buildDownloadUrl(String objectKey) {
        String apiBase = appBaseUrl.endsWith("/") ? appBaseUrl.substring(0, appBaseUrl.length() - 1) : appBaseUrl;
        return apiBase + "/api/files/download/" + encodeObjectKeyForUrl(objectKey);
    }

    public static String encodeObjectKeyForUrl(String objectKey) {
        return Arrays.stream(objectKey.split("/"))
                .map(seg -> URLEncoder.encode(seg, StandardCharsets.UTF_8))
                .collect(Collectors.joining("/"));
    }

    public static String decodeObjectKeyFromUrl(String encodedPath) {
        if (encodedPath == null || encodedPath.isBlank()) {
            return encodedPath;
        }
        return Arrays.stream(encodedPath.split("/"))
                .map(seg -> URLDecoder.decode(seg, StandardCharsets.UTF_8))
                .collect(Collectors.joining("/"));
    }

    @Override
    public byte[] downloadFile(String fileName) {
        try {
            if ("dummy".equals(bucketName) || bucketName == null || bucketName.isEmpty()) {
                java.nio.file.Path filePath = java.nio.file.Paths.get("uploads").resolve(fileName.replace("/", "_"));
                if (java.nio.file.Files.exists(filePath)) {
                    return java.nio.file.Files.readAllBytes(filePath);
                }
            }

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .build();

            ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(getObjectRequest);
            return objectBytes.asByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to download file from Cloudflare R2", e);
        }
    }
}
