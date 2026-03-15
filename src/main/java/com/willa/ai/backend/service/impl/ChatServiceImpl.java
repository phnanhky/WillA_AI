package com.willa.ai.backend.service.impl;

import com.willa.ai.backend.dto.request.ChatMessageRequest;
import com.willa.ai.backend.dto.request.ChatSessionRequest;
import com.willa.ai.backend.dto.response.ChatMessageResponse;
import com.willa.ai.backend.dto.response.ChatSessionResponse;
import com.willa.ai.backend.entity.ChatMessage;
import com.willa.ai.backend.entity.ChatSession;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.exception.ResourceNotFoundException;
import com.willa.ai.backend.repository.ChatMessageRepository;
import com.willa.ai.backend.repository.ChatSessionRepository;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.service.ChatService;
import com.willa.ai.backend.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final WalletService walletService; 

    @Override
    @Transactional
    public ChatSessionResponse createSession(String email, ChatSessionRequest request) {
        User user = getUserByEmail(email);
        
        ChatSession session = ChatSession.builder()
                .user(user)
                .title(request.getTitle())
                .isActive(true)
                .build();
                
        ChatSession savedSession = chatSessionRepository.save(session);
        return mapToSessionResponse(savedSession);
    }

    @Override
    public Page<ChatSessionResponse> getUserSessions(String email, int page, int size) {
        User user = getUserByEmail(email);
        Pageable pageable = PageRequest.of(page, size);
        return chatSessionRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(user.getId(), pageable)
                .map(this::mapToSessionResponse);
    }

    @Override
    public ChatSessionResponse getSessionById(String email, Long sessionId) {
        ChatSession session = getSessionEntity(email, sessionId);
        return mapToSessionResponse(session);
    }

    @Override
    @Transactional
    public ChatSessionResponse updateSessionTitle(String email, Long sessionId, ChatSessionRequest request) {
        ChatSession session = getSessionEntity(email, sessionId);
        session.setTitle(request.getTitle());
        return mapToSessionResponse(chatSessionRepository.save(session));
    }

    @Override
    @Transactional
    public void deleteSession(String email, Long sessionId) {
        ChatSession session = getSessionEntity(email, sessionId);
        session.setIsActive(false); 
        chatSessionRepository.save(session);
    }

    @Override
    @Transactional
    public ChatMessageResponse addMessageToSession(String email, Long sessionId, ChatMessageRequest request) {
        ChatSession session = getSessionEntity(email, sessionId);

        int tokensUsed = request.getTokensUsed() != null ? request.getTokensUsed() : 0;
        if (tokensUsed > 0) {
            walletService.deductTokens(email, (long) tokensUsed);
        }

        ChatMessage message = ChatMessage.builder()
                .session(session)
                .role(request.getRole())
                .content(request.getContent())
                .imageUrl(request.getImageUrl())
                .tokensUsed(tokensUsed)
                .build();

        ChatMessage savedMessage = chatMessageRepository.save(message);
        return mapToMessageResponse(savedMessage);
    }

    @Override
    public Page<ChatMessageResponse> getSessionMessages(String email, Long sessionId, int page, int size) {
        ChatSession session = getSessionEntity(email, sessionId); 
        Pageable pageable = PageRequest.of(page, size);
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId(), pageable)
                .map(this::mapToMessageResponse);
    }

    @Override
    public List<ChatMessageResponse> getAllSessionMessages(String email, Long sessionId) {
        ChatSession session = getSessionEntity(email, sessionId); 
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId())
                .stream().map(this::mapToMessageResponse)
                .collect(Collectors.toList());
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    private ChatSession getSessionEntity(String email, Long sessionId) {
        User user = getUserByEmail(email);
        return chatSessionRepository.findByIdAndUserIdAndIsActiveTrue(sessionId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Chat Session not found or deleted"));
    }

    private ChatSessionResponse mapToSessionResponse(ChatSession session) {
        return ChatSessionResponse.builder()
                .id(session.getId())
                .title(session.getTitle())
                .isActive(session.getIsActive())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }

    private ChatMessageResponse mapToMessageResponse(ChatMessage message) {
        return ChatMessageResponse.builder()
                .id(message.getId())
                .sessionId(message.getSession().getId())
                .role(message.getRole())
                .content(message.getContent())
                .imageUrl(message.getImageUrl())
                .tokensUsed(message.getTokensUsed())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
