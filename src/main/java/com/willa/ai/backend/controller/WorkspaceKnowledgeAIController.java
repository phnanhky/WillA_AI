package com.willa.ai.backend.controller;

import com.willa.ai.backend.dto.request.WorkspaceKnowledgeAIRequest;
import com.willa.ai.backend.dto.response.ApiResponse;
import com.willa.ai.backend.service.WorkspaceKnowledgeAIService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/knowledge-ai")
@RequiredArgsConstructor
@Tag(name = "Knowledge AI", description = "Workspace Knowledge Base Q&A")
@SecurityRequirement(name = "bearerAuth")
public class WorkspaceKnowledgeAIController {

    private final WorkspaceKnowledgeAIService knowledgeAIService;

    @PostMapping("/chat")
    @Operation(summary = "Trò chuyện với trợ lý AI của Workspace")
    public ResponseEntity<ApiResponse> chatWithKnowledgeAI(
            Authentication auth,
            @PathVariable Long workspaceId,
            @Valid @RequestBody WorkspaceKnowledgeAIRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Chat processed")
                    .data(knowledgeAIService.processChat(auth.getName(), workspaceId, request))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }
}
