package com.willa.ai.backend.service.impl;

import com.willa.ai.backend.dto.request.ChatMessageRequest;
import com.willa.ai.backend.dto.request.ChatSessionRequest;
import com.willa.ai.backend.dto.response.ChatMessageResponse;
import com.willa.ai.backend.dto.response.ChatSessionResponse;
import com.willa.ai.backend.entity.*;
import com.willa.ai.backend.entity.enums.MessageRole;
import com.willa.ai.backend.exception.ResourceNotFoundException;
import com.willa.ai.backend.repository.*;
import com.willa.ai.backend.service.ChatService;
import com.willa.ai.backend.service.FileService;
import com.willa.ai.backend.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final WalletService walletService; 
    private final FileService fileService;
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
    public ChatMessageResponse sendMessageToAi(String email, Long sessionId, String content, String actionType, Integer errorIndex, MultipartFile file) {
        User user = getUserByEmail(email);

        Wallet wallet = walletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Wallet not found"));

        if (wallet.getTokenBalance() <= 0) {
            throw new RuntimeException("Số dư token của bạn đã hết. Vui lòng nạp thêm hoặc nâng cấp gói để tiếp tục.");
        }

        ChatSession session = getSessionEntity(email, sessionId);
        
        String imageUrl = null;
        if (file != null && !file.isEmpty()) {
            imageUrl = fileService.uploadFile(file);
        }

        // 1. Lưu message của user
        ChatMessage userMessage = ChatMessage.builder()
                .session(session)
                .role(MessageRole.USER)
                .content(content)
                .imageUrl(imageUrl)
                .tokensUsed(0) 
                .build();
        chatMessageRepository.save(userMessage);

        String aiResponseContent = "";
        int totalTokens = 0;
        int promptTokens = 0;
        int completionTokens = 0;
        String modelUsed = "qwen3-vl-flash"; // Fixed model name
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode;

            // GỌI UNIFIED API /chat (BẮT BUỘC MULTIPART)
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            
            if (content != null && !content.trim().isEmpty()) {
                body.add("message", content);
            }
            body.add("session_id", sessionId.toString());
            body.add("user_id", user.getId().toString());

            if (actionType != null && !actionType.trim().isEmpty()) {
                body.add("action_type", actionType);
            }
            if (errorIndex != null) {
                body.add("error_index", errorIndex.toString());
            }

            if (file != null && !file.isEmpty()) {
                body.add("file", file.getResource());
            } else if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                // TỐI ƯU MEMORY STREAMING: Tránh OOM (Tràn RAM) khi có nhiều user tải ảnh
                if (imageUrl.startsWith("data:image")) {
                    // Nếu là base64, buộc phải parse ở RAM
                    String[] parts = imageUrl.split(",");
                    if (parts.length > 1) {
                        byte[] imageBytes = java.util.Base64.getDecoder().decode(parts[1]);
                        ByteArrayResource contentsAsResource = new ByteArrayResource(imageBytes) {
                            @Override
                            public String getFilename() {
                                return "image.jpg";
                            }
                        };
                        body.add("file", contentsAsResource);
                    } else {
                        throw new RuntimeException("Invalid base64 image data");
                    }
                } else {
                    // Nếu là URL, dùng InputStreamResource truyền trực tiếp theo luồng (Stream) sang Python
                    String urlStr = imageUrl;
                    String filename = "image.jpg";
                    if (urlStr.contains("/")) {
                        String[] parts = urlStr.split("/");
                        filename = parts[parts.length - 1];
                        if (filename.contains("?")) filename = filename.split("\\?")[0];
                    }
                    final String finalFilename = (!filename.isEmpty()) ? filename : "image.jpg";
                    
                    try {
                        URL urlToStream = new URI(urlStr).toURL();
                        InputStreamResource streamResource = new InputStreamResource(urlToStream.openStream()) {
                            @Override
                            public String getFilename() {
                                return finalFilename;
                            }
                            @Override
                            public long contentLength() {
                                return -1; // Để RestTemplate tự chia chunk
                            }
                        };
                        body.add("file", streamResource);
                    } catch (Exception ex) {
                        throw new RuntimeException("Failed to stream image from URL: " + ex.getMessage());
                    }
                }
            }

            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
            String chatUrl = aiServerUrl.endsWith("/") ? aiServerUrl + "chat" : aiServerUrl + "/chat";
            
            ResponseEntity<String> responseEntity = restTemplate.postForEntity(chatUrl, entity, String.class);
            rootNode = mapper.readTree(responseEntity.getBody());
            
            // Xử lý kết quả trả về
            aiResponseContent = responseEntity.getBody(); // Giữ nguyên toàn bộ JSON trả về để Frontend dùng
            
            // Lấy token chuẩn từ Usage của Server AI trả về
            if (rootNode.has("usage")) {
                JsonNode usageNode = rootNode.get("usage");
                if (usageNode.has("total_tokens")) totalTokens = usageNode.get("total_tokens").asInt();
                if (usageNode.has("input_tokens")) promptTokens = usageNode.get("input_tokens").asInt();
                if (usageNode.has("output_tokens")) completionTokens = usageNode.get("output_tokens").asInt();
            } else {
                // Sẽ không nhảy vào đây nếu AI trả về chuẩn, dự phòng:
                promptTokens = 1000;
                completionTokens = 500;
                totalTokens = 1500;
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to call AI or parse response: " + e.getMessage());
        }

        // 4. Trừ Token (Cho phép về 0 nếu totalTokens > tokenBalance)
        if (totalTokens > 0) {
            Long currentBalance = wallet.getTokenBalance();
            Long newBalance = currentBalance - totalTokens;
            if (newBalance < 0) {
                newBalance = 0L;
            }
            wallet.setTokenBalance(newBalance);
            walletRepository.save(wallet);

            AITokenUsage aiTokenUsage = AITokenUsage.builder()
                    .user(user)
                    .model(modelUsed)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(totalTokens)
                    .serviceType((imageUrl != null && !imageUrl.trim().isEmpty()) ? "ANALYZE" : "CHAT")
                    .build();
            aiTokenUsageRepository.save(aiTokenUsage);
        }
        // 5. Lưu kết quả của AI
        ChatMessage aiMessage = ChatMessage.builder()
                .session(session)
                .role(MessageRole.AI)
                .content(aiResponseContent)
                .imageUrl(imageUrl)
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
        ObjectMapper mapper = new ObjectMapper();
        Object contentObj;
        try {
            if (message.getContent() != null && (message.getContent().trim().startsWith("{") || message.getContent().trim().startsWith("["))) {
                contentObj = mapper.readTree(message.getContent());
            } else {
                contentObj = message.getContent();
            }
        } catch (Exception e) {
            contentObj = message.getContent();
        }

        return ChatMessageResponse.builder()
                .id(message.getId())
                .sessionId(message.getSession().getId())
                .role(message.getRole())
                .content(contentObj)
                .imageUrl(message.getImageUrl())
                .tokensUsed(message.getTokensUsed())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
