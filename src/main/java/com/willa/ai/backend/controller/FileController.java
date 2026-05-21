package com.willa.ai.backend.controller;

import com.willa.ai.backend.service.impl.AdvancedFileParserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = "*")
public class FileController {

    @Autowired
    private AdvancedFileParserService fileParserService;

    @Autowired
    private com.willa.ai.backend.service.FileService fileService;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            String url = fileService.uploadFile(file);
            return ResponseEntity.ok(Map.of("success", true, "data", url));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Upload failed: " + e.getMessage()));
        }
    }

    @GetMapping("/download/{fileName}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String fileName) {
        try {
            byte[] data = fileService.downloadFile(fileName);
            String contentType = "application/octet-stream";
            String lower = fileName.toLowerCase();
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                contentType = "image/jpeg";
            } else if (lower.endsWith(".png")) {
                contentType = "image/png";
            } else if (lower.endsWith(".webp")) {
                contentType = "image/webp";
            } else if (lower.endsWith(".gif")) {
                contentType = "image/gif";
            }
            return ResponseEntity.ok()
                    .header("Content-Type", contentType)
                    .header("Cache-Control", "public, max-age=31536000, immutable")
                    .header("Access-Control-Allow-Origin", "*")
                    .body(data);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
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
