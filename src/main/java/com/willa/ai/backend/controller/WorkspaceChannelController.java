package com.willa.ai.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.willa.ai.backend.dto.request.WorkspaceChannelRequest;
import com.willa.ai.backend.dto.request.WorkspaceChatMessageRequest;
import com.willa.ai.backend.dto.response.ApiResponse;
import com.willa.ai.backend.service.WorkspaceChannelService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/channels")
@RequiredArgsConstructor
@Tag(name = "Workspace Channel", description = "Kênh chat trong workspace (Discord-like)")
@SecurityRequirement(name = "bearerAuth")
public class WorkspaceChannelController {

    private final WorkspaceChannelService workspaceChannelService;

    @GetMapping
    @Operation(summary = "Danh sách kênh")
    public ResponseEntity<ApiResponse> listChannels(Authentication auth, @PathVariable Long workspaceId) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Channels retrieved")
                    .data(workspaceChannelService.listChannels(auth.getName(), workspaceId))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PostMapping
    @Operation(summary = "Tạo kênh mới")
    public ResponseEntity<ApiResponse> createChannel(
            Authentication auth,
            @PathVariable Long workspaceId,
            @Valid @RequestBody WorkspaceChannelRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Channel created")
                    .data(workspaceChannelService.createChannel(auth.getName(), workspaceId, request))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PutMapping("/{channelId}")
    @Operation(summary = "Đổi tên kênh")
    public ResponseEntity<ApiResponse> updateChannel(
            Authentication auth,
            @PathVariable Long workspaceId,
            @PathVariable Long channelId,
            @Valid @RequestBody WorkspaceChannelRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Channel updated")
                    .data(workspaceChannelService.updateChannel(auth.getName(), workspaceId, channelId, request))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @DeleteMapping("/{channelId}")
    @Operation(summary = "Xóa kênh")
    public ResponseEntity<ApiResponse> deleteChannel(
            Authentication auth,
            @PathVariable Long workspaceId,
            @PathVariable Long channelId) {
        try {
            workspaceChannelService.deleteChannel(auth.getName(), workspaceId, channelId);
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Channel deleted")
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @GetMapping("/{channelId}/messages")
    @Operation(summary = "Lịch sử tin nhắn kênh")
    public ResponseEntity<ApiResponse> listChannelMessages(
            Authentication auth,
            @PathVariable Long workspaceId,
            @PathVariable Long channelId) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Messages retrieved")
                    .data(workspaceChannelService.listChannelMessages(auth.getName(), workspaceId, channelId))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PostMapping("/{channelId}/messages")
    @Operation(summary = "Gửi tin nhắn vào kênh")
    public ResponseEntity<ApiResponse> sendChannelMessage(
            Authentication auth,
            @PathVariable Long workspaceId,
            @PathVariable Long channelId,
            @Valid @RequestBody WorkspaceChatMessageRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Message sent")
                    .data(workspaceChannelService.sendChannelMessage(auth.getName(), workspaceId, channelId, request))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }
}
