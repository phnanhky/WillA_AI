package com.willa.ai.backend.controller;

import com.willa.ai.backend.service.FileService;
import com.willa.ai.backend.service.impl.AdvancedFileParserService;
import com.willa.ai.backend.service.impl.FileServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {

    @Autowired
    private AdvancedFileParserService fileParserService;

    @Autowired
    private FileService fileService;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            String url = fileService.uploadFile(file);
            return ResponseEntity.ok(Map.of("success", true, "data", url));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Upload failed: " + e.getMessage()));
        }
    }

    @PostMapping("/upload-document")
    public ResponseEntity<?> uploadDocument(@RequestParam("file") MultipartFile file) {
        try {
            String url = fileService.uploadDocument(file);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", url,
                    "fileName", file.getOriginalFilename() != null ? file.getOriginalFilename() : "file",
                    "fileSize", file.getSize(),
                    "contentType", file.getContentType() != null ? file.getContentType() : "application/octet-stream"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Upload failed: " + e.getMessage()));
        }
    }

    @GetMapping("/download/**")
    public ResponseEntity<byte[]> downloadFile(HttpServletRequest request) {
        try {
            String uri = request.getRequestURI();
            String marker = "/api/files/download/";
            int idx = uri.indexOf(marker);
            if (idx < 0) {
                return ResponseEntity.notFound().build();
            }
            String encodedKey = uri.substring(idx + marker.length());
            String fileKey = FileServiceImpl.decodeObjectKeyFromUrl(encodedKey);
            byte[] data = fileService.downloadFile(fileKey);
            String contentType = resolveContentType(fileKey);
            String filename = fileKey.contains("/")
                    ? fileKey.substring(fileKey.lastIndexOf('/') + 1)
                    : fileKey;
            boolean inlineImage = contentType.startsWith("image/");
            String disposition = (inlineImage ? "inline" : "attachment")
                    + "; filename=\"" + filename.replace("\"", "") + "\"";
            return ResponseEntity.ok()
                    .header("Content-Type", contentType)
                    .header("Content-Disposition", disposition)
                    .header("Cache-Control", "public, max-age=31536000, immutable")
                    .body(data);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private static String resolveContentType(String fileKey) {
        String lower = fileKey != null ? fileKey.toLowerCase() : "";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".zip")) return "application/zip";
        if (lower.endsWith(".psd")) return "image/vnd.adobe.photoshop";
        if (lower.endsWith(".ai")) return "application/postscript";
        if (lower.endsWith(".csv")) return "text/csv";
        return "application/octet-stream";
    }

    @PostMapping("/parse")
    public ResponseEntity<?> parseFile(@RequestParam("file") MultipartFile file) {
        try {
            Map<String, Object> result = fileParserService.parseFile(file);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to parse file: " + e.getMessage()));
        }
    }
}
