package com.willa.ai.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.willa.ai.backend.dto.request.WorkspaceChatMessageRequest;
import com.willa.ai.backend.dto.response.ApiResponse;
import com.willa.ai.backend.service.WorkspaceChannelService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/dms")
@RequiredArgsConstructor
@Tag(name = "Workspace DM", description = "Chat riêng giữa thành viên workspace")
@SecurityRequirement(name = "bearerAuth")
public class WorkspaceDmController {

    private final WorkspaceChannelService workspaceChannelService;

    @GetMapping("/{peerUserId}/messages")
    @Operation(summary = "Lịch sử tin nhắn riêng với thành viên")
    public ResponseEntity<ApiResponse> listDmMessages(
            Authentication auth,
            @PathVariable Long workspaceId,
            @PathVariable Long peerUserId) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("DM messages retrieved")
                    .data(workspaceChannelService.listDmMessages(auth.getName(), workspaceId, peerUserId))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PostMapping("/{peerUserId}/messages")
    @Operation(summary = "Gửi tin nhắn riêng cho thành viên")
    public ResponseEntity<ApiResponse> sendDmMessage(
            Authentication auth,
            @PathVariable Long workspaceId,
            @PathVariable Long peerUserId,
            @Valid @RequestBody WorkspaceChatMessageRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("DM sent")
                    .data(workspaceChannelService.sendDmMessage(auth.getName(), workspaceId, peerUserId, request))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }
}
