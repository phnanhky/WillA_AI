package com.willa.ai.backend.controller;

import io.swagger.v3.oas.annotations.Operation;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.willa.ai.backend.dto.ApiResponse;
import com.willa.ai.backend.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
@CrossOrigin("*")
@Tag(name = "File Management", description = "APIs for File Management")
public class FileController {

    @Autowired
    private FileService fileService;

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<String>> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            String fileUrl = fileService.uploadFile(file);
            return ResponseEntity.ok(ApiResponse.<String>builder()
                    .success(true)
                    .message("File uploaded successfully")
                    .data(fileUrl)
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.<String>builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }
}