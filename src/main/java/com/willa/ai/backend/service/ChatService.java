package com.willa.ai.backend.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import com.willa.ai.backend.dto.request.ChatMessageRequest;
import com.willa.ai.backend.dto.request.ChatSessionRequest;
import com.willa.ai.backend.dto.response.ChatMessageResponse;
import com.willa.ai.backend.dto.response.ChatSessionResponse;

public interface ChatService {
    // Session CRUD
    ChatSessionResponse createSession(String email, ChatSessionRequest request);
    Page<ChatSessionResponse> getUserSessions(String email, int page, int size);
    ChatSessionResponse getSessionById(String email, Long sessionId);
    ChatSessionResponse updateSessionTitle(String email, Long sessionId, ChatSessionRequest request);
    void deleteSession(String email, Long sessionId);

    // Message CRUD
    ChatMessageResponse addMessageToSession(String email, Long sessionId, ChatMessageRequest request);
    Page<ChatMessageResponse> getSessionMessages(String email, Long sessionId, int page, int size);
    List<ChatMessageResponse> getAllSessionMessages(String email, Long sessionId);
    
    // AI Chat Integration
    ChatMessageResponse sendMessageToAi(String email, Long sessionId, String content, String actionType, Integer errorIndex, String box2d, Integer imageIndex, String replyLang, List<MultipartFile> files);

    // AI Image Generation (text→image hoặc image→image qua regen)
    ChatMessageResponse generateImage(String email, Long sessionId, String prompt, List<MultipartFile> files);

    // AI Inpaint Integration
    Object prepareRegen(String email, Long sessionId, String errorIndices, Integer imageIndex);

    Object regenImage(String email, Long sessionId, String errorIndices, String finalPrompt, Integer imageIndex);

    Object suggestStyle(String email, MultipartFile file, String box2d, String suggestType);

    Object extractLayers(String email, String imageBase64, String mimeType, int numLayers);
}
