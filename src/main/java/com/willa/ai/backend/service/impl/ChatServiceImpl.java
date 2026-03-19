package com.willa.ai.backend.service.impl;

import com.willa.ai.backend.dto.request.ChatMessageRequest;
import com.willa.ai.backend.dto.request.ChatSessionRequest;
import com.willa.ai.backend.dto.response.ChatMessageResponse;
import com.willa.ai.backend.dto.response.ChatSessionResponse;
import com.willa.ai.backend.entity.ChatMessage;
import com.willa.ai.backend.entity.ChatSession;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.AITokenUsage;
import com.willa.ai.backend.entity.enums.MessageRole;
import com.willa.ai.backend.exception.ResourceNotFoundException;
import com.willa.ai.backend.repository.AITokenUsageRepository;
import com.willa.ai.backend.repository.ChatMessageRepository;
import com.willa.ai.backend.repository.ChatSessionRepository;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.service.ChatService;
import com.willa.ai.backend.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final WalletService walletService; 
    private final RestTemplate restTemplate;
    private final AITokenUsageRepository aiTokenUsageRepository;

    @Value("${ai.server.url}")
    private String aiServerUrl;

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

    @Override
    @Transactional
    public ChatMessageResponse sendMessageToAi(String email, Long sessionId, ChatMessageRequest request) {
        User user = getUserByEmail(email);
        ChatSession session = getSessionEntity(email, sessionId);
        
        // 1. Lưu message của user
        ChatMessage userMessage = ChatMessage.builder()
                .session(session)
                .role(MessageRole.USER)
                .content(request.getContent())
                .imageUrl(request.getImageUrl())
                .tokensUsed(0) 
                .build();
        chatMessageRepository.save(userMessage);

        // 2. Gọi sang AI Server
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // Giả lập DTO body chuẩn cho /v1/chat/completions
        Map<String, Object> body = new HashMap<>();
        body.put("model", "qwen3-vl-flash");
        body.put("messages", List.of(Map.of("role", "user", "content", request.getContent())));
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        
        ResponseEntity<String> responseEntity = restTemplate.postForEntity(aiServerUrl, entity, String.class);
        
        // 3. Phân tích kết quả JSON trả về
        String responseBody = responseEntity.getBody();
        String aiResponseContent = "";
        int totalTokens = 0;
        int promptTokens = 0;
        int completionTokens = 0;
        String modelUsed = "unknown";
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(responseBody);
            
            // Lấy content
            if (rootNode.has("choices")) {
                JsonNode messageNode = rootNode.get("choices").get(0).get("message");
                if (messageNode != null && messageNode.has("content")) {
                    aiResponseContent = messageNode.get("content").asText();
                }
            }
            
            // Lấy token và lưu
            if (rootNode.has("usage")) {
                JsonNode usageNode = rootNode.get("usage");
                if (usageNode.has("total_tokens")) totalTokens = usageNode.get("total_tokens").asInt();
                if (usageNode.has("prompt_tokens")) promptTokens = usageNode.get("prompt_tokens").asInt();
                if (usageNode.has("completion_tokens")) completionTokens = usageNode.get("completion_tokens").asInt();
            }
            
            if (rootNode.has("model")) {
                modelUsed = rootNode.get("model").asText();
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse AI response: " + e.getMessage());
        }

        // 4. Trừ Token
        if (totalTokens > 0) {
            boolean success = walletService.deductTokens(email, (long) totalTokens);
            if (!success) {
                throw new RuntimeException("Insufficient tokens for this AI operation.");
            }
            
            // Lưu log token Usage
            AITokenUsage aiTokenUsage = AITokenUsage.builder()
                .user(user)
                .model(modelUsed)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(totalTokens)
                .serviceType("CHAT")
                .build();
            aiTokenUsageRepository.save(aiTokenUsage);
        }

        // 5. Lưu kết quả của AI
        ChatMessage aiMessage = ChatMessage.builder()
                .session(session)
                .role(MessageRole.AI)
                .content(aiResponseContent)
                .tokensUsed(totalTokens)
                .build();
                
        ChatMessage savedAiMessage = chatMessageRepository.save(aiMessage);
        
        return mapToMessageResponse(savedAiMessage);
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
