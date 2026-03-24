package com.willa.ai.backend.service;

import com.willa.ai.backend.dto.request.ChatMessageRequest;
import com.willa.ai.backend.dto.request.ChatSessionRequest;
import com.willa.ai.backend.dto.response.ChatMessageResponse;
import com.willa.ai.backend.dto.response.ChatSessionResponse;
import org.springframework.data.domain.Page;

import java.util.List;

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
    ChatMessageResponse sendMessageToAi(String email, Long sessionId, String content, String actionType, Integer errorIndex, org.springframework.web.multipart.MultipartFile file);
}
