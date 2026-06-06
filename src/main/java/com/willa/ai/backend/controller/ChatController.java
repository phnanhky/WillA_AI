package com.willa.ai.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.willa.ai.backend.dto.request.ChatMessageRequest;
import com.willa.ai.backend.dto.request.ChatSessionRequest;
import com.willa.ai.backend.dto.request.ExtractLayersRequest;
import com.willa.ai.backend.dto.response.ApiResponse;
import com.willa.ai.backend.dto.response.ChatMessageResponse;
import com.willa.ai.backend.dto.response.ChatSessionResponse;
import com.willa.ai.backend.exception.InsufficientTokenException;
import com.willa.ai.backend.service.ChatService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/chats")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "AI Chat Sessions and Messages APIs")
@SecurityRequirement(name = "bearerAuth") // THÊM SECURITY CHO SWAGGER HIỂU
public class ChatController {

    private final ChatService chatService;

    // --- SESSION APIs ---

    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse> createSession(
            @Valid @RequestBody ChatSessionRequest request,
            Authentication authentication) {
        ChatSessionResponse session = chatService.createSession(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("Chat session created successfully")
                .data(session)
                .build());
    }

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse> getUserSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        Page<ChatSessionResponse> sessions = chatService.getUserSessions(authentication.getName(), page, size);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("Chat sessions retrieved successfully")
                .data(sessions)
                .build());
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse> getSessionById(
            @PathVariable Long sessionId,
            Authentication authentication) {
        ChatSessionResponse session = chatService.getSessionById(authentication.getName(), sessionId);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("Chat session retrieved successfully")
                .data(session)
                .build());
    }

    @PutMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse> updateSessionTitle(
            @PathVariable Long sessionId,
            @Valid @RequestBody ChatSessionRequest request,
            Authentication authentication) {
        ChatSessionResponse session = chatService.updateSessionTitle(authentication.getName(), sessionId, request);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("Chat session renamed successfully")
                .data(session)
                .build());
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse> deleteSession(
            @PathVariable Long sessionId,
            Authentication authentication) {
        chatService.deleteSession(authentication.getName(), sessionId);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("Chat session deleted successfully")
                .build());
    }

    // --- MESSAGE APIs ---

    @PostMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<ApiResponse> addMessageToSession(
            @PathVariable Long sessionId,
            @Valid @RequestBody ChatMessageRequest request,
            Authentication authentication) {
        ChatMessageResponse message = chatService.addMessageToSession(authentication.getName(), sessionId, request);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("Message added successfully")
                .data(message)
                .build());
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<ApiResponse> getSessionMessages(
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        Page<ChatMessageResponse> messages = chatService.getSessionMessages(authentication.getName(), sessionId, page, size);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("Messages retrieved successfully")
                .data(messages)
                .build());
    }

    @GetMapping("/sessions/{sessionId}/messages/all")
    public ResponseEntity<ApiResponse> getAllSessionMessages(
            @PathVariable Long sessionId,
            Authentication authentication) {
        List<ChatMessageResponse> messages = chatService.getAllSessionMessages(authentication.getName(), sessionId);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("All messages retrieved successfully")
                .data(messages)
                .build());
    }

    @PostMapping(value = "/sessions/{sessionId}/send-message", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    @Operation(summary = "Gửi tin nhắn từ User lên AI qua trung gian BE (Hỗ trợ upload ảnh)")
    public ResponseEntity<ApiResponse> sendMessageToAi(
            @PathVariable Long sessionId,
            @Parameter(description = "Nội dung tin nhắn") @RequestParam(value = "content", required = false) String content,
            @Parameter(description = "Loại hành động (VD: zoom)") @RequestParam(value = "actionType", required = false) String actionType,
            @Parameter(description = "Index của lỗi nếu dùng zoom") @RequestParam(value = "errorIndex", required = false) Integer errorIndex,
            @Parameter(description = "Tọa độ pixel [x1,y1,x2,y2] JSON — ưu tiên hơn errorIndex khi zoom") @RequestParam(value = "box2d", required = false) String box2d,
            @Parameter(description = "Pro multi-image: 0 = ảnh 1, 1 = ảnh 2") @RequestParam(value = "imageIndex", required = false) Integer imageIndex,
            @Parameter(description = "Ngôn ngữ UI: vi | en") @RequestParam(value = "replyLang", required = false) String replyLang,
            @Parameter(description = "File ảnh upload lên") @RequestPart(value = "files", required = false) List<MultipartFile> files,
            Authentication authentication) {

        try {
            if (authentication == null || authentication.getName() == null) {
                return ResponseEntity.status(401).body(ApiResponse.builder()
                        .status(false)
                        .message("Unauthorized: Security token is missing or invalid in multipart form data")
                        .build());
            }

            ChatMessageResponse response = chatService.sendMessageToAi(authentication.getName(), sessionId, content, actionType, errorIndex, box2d, imageIndex, replyLang, files);
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Message processed by AI")
                    .data(response)
                    .build());
        } catch (InsufficientTokenException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .status(false)
                    .message(e.getMessage() != null ? e.getMessage() : "Error occurred while calling AI")
                    .build());
        }
    }

    @PostMapping(value = "/sessions/{sessionId}/generate", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    @Operation(summary = "Tạo ảnh từ mô tả (text→image qua Grok/xAI)")
    public ResponseEntity<ApiResponse> generateImage(
            @PathVariable Long sessionId,
            @Parameter(description = "Mô tả ảnh cần tạo") @RequestParam("content") String content,
            @Parameter(description = "Ảnh tham chiếu (tùy chọn, để tạo lại theo mô tả)") @RequestPart(value = "files", required = false) List<MultipartFile> files,
            Authentication authentication) {
        try {
            if (authentication == null || authentication.getName() == null) {
                return ResponseEntity.status(401).body(ApiResponse.builder()
                        .status(false)
                        .message("Unauthorized")
                        .build());
            }
            ChatMessageResponse response = chatService.generateImage(authentication.getName(), sessionId, content, files);
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Image generated by AI")
                    .data(response)
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .status(false)
                    .message(e.getMessage() != null ? e.getMessage() : "Error occurred while generating image")
                    .build());
        }
    }

    @PostMapping("/prepare-regen")
    public ResponseEntity<ApiResponse> prepareRegen(
            Authentication authentication,
            @RequestParam("sessionId") Long sessionId,
            @RequestParam(value = "errorIndices", required = false) String errorIndices,
            @RequestParam(value = "imageIndex", required = false) Integer imageIndex) {
        try {
            Object result = chatService.prepareRegen(authentication.getName(), sessionId, errorIndices, imageIndex);
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Prepare regen successful")
                    .data(result)
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .status(false)
                    .message(e.getMessage() != null ? e.getMessage() : "Prepare regen failed")
                    .build());
        }
    }

    @PostMapping("/regen-image")
    public ResponseEntity<ApiResponse> regenImage(
            Authentication authentication,
            @RequestParam("sessionId") Long sessionId,
            @RequestParam(value = "errorIndices", required = false) String errorIndices,
            @RequestParam(value = "finalPrompt", required = false) String finalPrompt,
            @RequestParam(value = "imageIndex", required = false) Integer imageIndex) {
        try {
            Object result = chatService.regenImage(authentication.getName(), sessionId, errorIndices, finalPrompt, imageIndex);
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Regen image successful")
                    .data(result)
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .status(false)
                    .message(e.getMessage() != null ? e.getMessage() : "Regen image failed")
                    .build());
        }
    }

    @PostMapping(value = "/suggest-style", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse> suggestStyle(
            Authentication authentication,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "box_2d", defaultValue = "[]") String box2d,
            @RequestParam(value = "suggest_type", defaultValue = "typo") String suggestType) {
        try {
            Object result = chatService.suggestStyle(authentication.getName(), file, box2d, suggestType);
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Suggest style successful")
                    .data(result)
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .status(false)
                    .message(e.getMessage() != null ? e.getMessage() : "Suggest style failed")
                    .build());
        }
    }

    @PostMapping("/extract-layers")
    public ResponseEntity<ApiResponse> extractLayers(
            Authentication authentication,
            @Valid @RequestBody ExtractLayersRequest request) {
        Object result = chatService.extractLayers(
                authentication.getName(),
                request.getImageBase64(),
                request.getMimeType(),
                request.getNumLayers() != null ? request.getNumLayers() : 5);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("Layers extracted successfully")
                .data(result)
                .build());
    }
}
