package com.willa.ai.backend.service.impl;

import com.willa.ai.backend.dto.request.ChatMessageRequest;
import com.willa.ai.backend.dto.request.ChatSessionRequest;
import com.willa.ai.backend.dto.response.ChatMessageResponse;
import com.willa.ai.backend.dto.response.ChatSessionResponse;
import com.willa.ai.backend.entity.*;
import com.willa.ai.backend.entity.enums.MessageRole;
import com.willa.ai.backend.entity.enums.SubscriptionStatus;
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
    private final SubscriptionRepository subscriptionRepository;
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
//        if (Boolean.TRUE.equals(user.getRequiresReview())) {
//            throw new RuntimeException("Bạn cần để lại đánh giá (Review) trước khi tiếp tục ở gói Free.");
//        }
        
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
//        if (Boolean.TRUE.equals(user.getRequiresReview())) {
//            throw new RuntimeException("Bạn cần để lại đánh giá (Review) trước khi tiếp tục ở gói Free.");
//        }
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
    public ChatMessageResponse sendMessageToAi(String email, Long sessionId, String content, String actionType, Integer errorIndex, List<MultipartFile> files) {
        User user = getUserByEmail(email);

//        if (Boolean.TRUE.equals(user.getRequiresReview())) {
//            throw new RuntimeException("Bạn cần để lại đánh giá (Review) trước khi tiếp tục ở gói Free.");
//        }
        Wallet wallet = walletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Wallet not found"));

        if (wallet.getTokenBalance() <= 0) {
            throw new RuntimeException("Số dư token của bạn đã hết. Vui lòng nạp thêm hoặc nâng cấp gói để tiếp tục.");
        }
        
        List<Subscription> activeSubs = subscriptionRepository.findByUserIdAndStatus(user.getId(), SubscriptionStatus.ACTIVE);
        String planName = "Free";
        if (!activeSubs.isEmpty()) {
            planName = activeSubs.get(0).getPlan().getName();
        }

        if (files != null && files.size() > 1) {
            if (planName.equalsIgnoreCase("Student") || planName.equalsIgnoreCase("Free")) {
                throw new RuntimeException("Gói " + planName + " chỉ cho phép gửi 1 ảnh mỗi lần phân tích. Vui lòng nâng cấp gói Pro để gửi nhiều ảnh cùng lúc.");
            }
        }

        ChatSession session = getSessionEntity(email, sessionId);

        List<String> uploadedUrls = new java.util.ArrayList<>();
        if (files != null && !files.isEmpty()) {
            for (MultipartFile file : files) {
                if (file != null && !file.isEmpty()) {
                    uploadedUrls.add(fileService.uploadFile(file));
                }
            }
        }
        String imageUrlStr = uploadedUrls.isEmpty() ? null : String.join(",", uploadedUrls);

        // 1. Lưu message của user
        ChatMessage userMessage = ChatMessage.builder()
                .session(session)
                .role(MessageRole.USER)
                .content(content)
                .imageUrl(imageUrlStr)
                .tokensUsed(0) 
                .build();
        chatMessageRepository.save(userMessage);

        String aiResponseContent = "";
        int totalTokensCombined = 0;
        String modelUsed = "qwen3-vl-flash"; 
        
        try {
            ObjectMapper mapper = new ObjectMapper();

            if (files == null || files.isEmpty()) {
                // --- KỊCH BẢN CHỈ CHAT TEXT ---
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                
                if (content != null && !content.trim().isEmpty()) body.add("message", content);
                body.add("session_id", sessionId.toString());
                body.add("user_id", user.getId().toString());
                if (actionType != null && !actionType.trim().isEmpty()) body.add("action_type", actionType);
                if (errorIndex != null) body.add("error_index", errorIndex.toString());

                HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
                String chatUrl = aiServerUrl.endsWith("/") ? aiServerUrl + "chat" : aiServerUrl + "/chat";
                ResponseEntity<String> responseEntity = restTemplate.postForEntity(chatUrl, entity, String.class);
                
                JsonNode rootNode = mapper.readTree(responseEntity.getBody());
                aiResponseContent = responseEntity.getBody();
                
                int totalTokens = 0, promptTokens = 0, completionTokens = 0;
                if (rootNode.has("usage")) {
                    JsonNode usageNode = rootNode.get("usage");
                    if (usageNode.has("total_tokens")) totalTokens = usageNode.get("total_tokens").asInt();
                    if (usageNode.has("input_tokens")) promptTokens = usageNode.get("input_tokens").asInt();
                    if (usageNode.has("output_tokens")) completionTokens = usageNode.get("output_tokens").asInt();
                } else {
                    promptTokens = 1000; completionTokens = 500; totalTokens = 1500;
                }
                
                if (totalTokens > 0) {
                    Long newBalance = wallet.getTokenBalance() - totalTokens;
                    wallet.setTokenBalance(newBalance < 0 ? 0L : newBalance);
                    walletRepository.save(wallet);

                    AITokenUsage aiTokenUsage = AITokenUsage.builder()
                            .user(user).model(modelUsed).promptTokens(promptTokens).completionTokens(completionTokens).totalTokens(totalTokens)
                            .serviceType("CHAT").build();
                    aiTokenUsageRepository.save(aiTokenUsage);
                }
                totalTokensCombined = totalTokens;
                
            } else {
                // --- KỊCH BẢN CÓ FILE (1 hoặc NHIỀU ẢNH) ---
                com.fasterxml.jackson.databind.node.ArrayNode arrayNode = mapper.createArrayNode();
                
                for (int i = 0; i < files.size(); i++) {
                    // 1. Kiểm tra Token trước khi chạy từng ảnh
                    if (wallet.getTokenBalance() <= 0) {
                        break; // Hết token -> Dừng vòng lặp ngay lập tức
                    }
                    
                    MultipartFile file = files.get(i);
                    if (file == null || file.isEmpty()) continue;
                    
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                    
                    if (content != null && !content.trim().isEmpty()) body.add("message", content);
                    body.add("session_id", sessionId.toString());
                    body.add("user_id", user.getId().toString());
                    if (actionType != null && !actionType.trim().isEmpty()) body.add("action_type", actionType);
                    if (errorIndex != null) body.add("error_index", errorIndex.toString());
                    
                    // IMPORTANT: FastAPI UploadFile requires a filename. Using MultipartFile.getResource()
                    // may yield a Resource without filename, causing AI server to skip image flow.
                    ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                        @Override
                        public String getFilename() {
                            return file.getOriginalFilename() != null ? file.getOriginalFilename() : "image";
                        }
                    };
                    body.add("file", fileResource);

                    HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
                    String chatUrl = aiServerUrl.endsWith("/") ? aiServerUrl + "chat" : aiServerUrl + "/chat";
                    
                    // Gọi AI cho riêng TỪNG ẢNH
                    ResponseEntity<String> responseEntity = restTemplate.postForEntity(chatUrl, entity, String.class);
                    JsonNode rootNode = mapper.readTree(responseEntity.getBody());
                    
                    // Thêm kết quả của ảnh này vào mảng JSON
                    arrayNode.add(rootNode);
                    
                    int totalTokens = 0, promptTokens = 0, completionTokens = 0;
                    if (rootNode.has("usage")) {
                        JsonNode usageNode = rootNode.get("usage");
                        if (usageNode.has("total_tokens")) totalTokens = usageNode.get("total_tokens").asInt();
                        if (usageNode.has("input_tokens")) promptTokens = usageNode.get("input_tokens").asInt();
                        if (usageNode.has("output_tokens")) completionTokens = usageNode.get("output_tokens").asInt();
                    } else {
                        promptTokens = 1000; completionTokens = 500; totalTokens = 1500;
                    }
                    
                    // 2. Trừ Token ngay lập tức sau mỗi lần xử lý 1 ảnh
                    if (totalTokens > 0) {
                        Long newBalance = wallet.getTokenBalance() - totalTokens;
                        wallet.setTokenBalance(newBalance < 0 ? 0L : newBalance);
                        walletRepository.save(wallet); 

                        AITokenUsage aiTokenUsage = AITokenUsage.builder()
                                .user(user).model(modelUsed).promptTokens(promptTokens).completionTokens(completionTokens).totalTokens(totalTokens)
                                .serviceType("ANALYZE").build();
                        aiTokenUsageRepository.save(aiTokenUsage);
                        
                        totalTokensCombined += totalTokens;
                    }
                }
                
                // Gom dữ liệu trả về 
                if (files.size() == 1) {
                    if (arrayNode.size() > 0) {
                        aiResponseContent = arrayNode.get(0).toString(); // Trả về Json bth nếu 1 ảnh
                    }
                } else {
                    aiResponseContent = arrayNode.toString(); // Trả về mảng [Json, Json...] nếu nhiều ảnh
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to call AI or parse response: " + e.getMessage());
        }
        
        // 5. Lưu kết quả của AI
        ChatMessage aiMessage = ChatMessage.builder()
                .session(session)
                .role(MessageRole.AI)
                .content(aiResponseContent)
                .imageUrl(imageUrlStr)
                .tokensUsed(totalTokensCombined)
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
//        if (Boolean.TRUE.equals(user.getRequiresReview())) {
//            throw new RuntimeException("Bạn cần để lại đánh giá (Review) trước khi tiếp tục ở gói Free.");
//        }
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
