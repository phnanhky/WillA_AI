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
import java.util.UUID;

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
        byte[] uploadData = prepared.data();
        String resolvedContentType = prepared.contentType();

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(uniqueFileName)
                    .contentType(resolvedContentType)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(uploadData));

            String apiBase = appBaseUrl.endsWith("/") ? appBaseUrl.substring(0, appBaseUrl.length() - 1) : appBaseUrl;
            return apiBase + "/api/files/download/" + uniqueFileName;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to Cloudflare R2", e);
        }
    }

    @Override
    public byte[] downloadFile(String fileName) {
        try {
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