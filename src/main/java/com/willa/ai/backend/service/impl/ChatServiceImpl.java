package com.willa.ai.backend.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
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
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.willa.ai.backend.dto.request.ChatMessageRequest;
import com.willa.ai.backend.dto.request.ChatSessionRequest;
import com.willa.ai.backend.dto.response.ChatMessageResponse;
import com.willa.ai.backend.dto.response.ChatSessionResponse;
import com.willa.ai.backend.entity.AITokenUsage;
import com.willa.ai.backend.entity.ChatMessage;
import com.willa.ai.backend.entity.ChatSession;
import com.willa.ai.backend.entity.Subscription;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.Wallet;
import com.willa.ai.backend.entity.enums.MessageRole;
import com.willa.ai.backend.entity.enums.SubscriptionStatus;
import com.willa.ai.backend.exception.ResourceNotFoundException;
import com.willa.ai.backend.repository.AITokenUsageRepository;
import com.willa.ai.backend.repository.ChatMessageRepository;
import com.willa.ai.backend.repository.ChatSessionRepository;
import com.willa.ai.backend.repository.SubscriptionRepository;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.repository.WalletRepository;
import com.willa.ai.backend.service.ChatService;
import com.willa.ai.backend.service.FileService;
import com.willa.ai.backend.service.WalletService;

import lombok.RequiredArgsConstructor;

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

    @Value("${ai.dashscope.api-key:}")
    private String dashscopeApiKey;

    @Value("${ai.xai.api-key:}")
    private String xaiApiKey;

    @Value("${ai.qwen.model:qwen3-vl-flash}")
    private String qwenModel;

    @Value("${ai.imgbb.api-key:}")
    private String imgbbApiKey;

    @Value("${ai.fal.key:}")
    private String falKey;

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
    @Transactional(readOnly = true)
    public Page<ChatSessionResponse> getUserSessions(String email, int page, int size) {
        User user = getUserByEmail(email);
        Pageable pageable = PageRequest.of(page, size);
        
        java.time.LocalDateTime cutoff = getHistoryCutoffDate(user.getId());
        return chatSessionRepository.findByUserIdAndIsActiveTrueAndCreatedAtAfterOrderByCreatedAtDesc(user.getId(), cutoff, pageable)
                .map(this::mapToSessionResponse);
    }

    @Override
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    public Page<ChatMessageResponse> getSessionMessages(String email, Long sessionId, int page, int size) {
        ChatSession session = getSessionEntity(email, sessionId); 
        Pageable pageable = PageRequest.of(page, size);
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId(), pageable)
                .map(this::mapToMessageResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getAllSessionMessages(String email, Long sessionId) {
        ChatSession session = getSessionEntity(email, sessionId); 
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId())
                .stream().map(this::mapToMessageResponse)
                .collect(Collectors.toList());
    }

    @Autowired
    private AdvancedFileParserService advancedFileParserService;

    @Override
    @Transactional
    public ChatMessageResponse sendMessageToAi(String email, Long sessionId, String content, String actionType, Integer errorIndex, List<MultipartFile> files) {
        User user = getUserByEmail(email);
        
        String planName = getPlanNameForUser(user.getId());
        if (Boolean.TRUE.equals(user.getRequiresReview()) && planName.equalsIgnoreCase("Free")) {
            throw new RuntimeException("Bạn cần để lại đánh giá (Review) trước khi tiếp tục ở gói Free.");
        }

        Wallet wallet = walletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Wallet not found"));

        if (wallet.getTokenBalance() <= 0) {
            throw new RuntimeException("Số dư token của bạn đã hết. Vui lòng nạp thêm hoặc nâng cấp gói để tiếp tục.");
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
                    String fn = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
                    if (fn.endsWith(".pdf") || fn.endsWith(".csv") || fn.endsWith(".psd")) {
                        try {
                            java.util.Map<String, Object> parseRest = advancedFileParserService.parseFile(file);
                            // Caching the result string as 'content' inside the vision model payload, 
                            // or uploading base64 data to get URL representations directly in the conversation.
                            if ("pdf".equals(parseRest.get("type"))) {
                                List<String> base64Images = (List<String>) parseRest.get("pages");
                                uploadedUrls.addAll(base64Images); // Adding directly to the ImageUrls of chat message for vision processing
                            } else if ("psd".equals(parseRest.get("type"))) {
                                uploadedUrls.add((String) parseRest.get("image"));
                            }
                        } catch (Exception e) {
                            throw new RuntimeException("Lỗi phân tích file đặc biệt: " + e.getMessage());
                        }
                    } else {
                        uploadedUrls.add(fileService.uploadFile(file));
                    }
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

    private String getPlanNameForUser(Long userId) {
        List<Subscription> activeSubs = subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
        if (!activeSubs.isEmpty()) {
            return activeSubs.get(0).getPlan().getName();
        }
        return "Free";
    }

    private java.time.LocalDateTime getHistoryCutoffDate(Long userId) {
        List<Subscription> activeSubs = subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
        if (!activeSubs.isEmpty()) {
            Subscription activeSub = activeSubs.get(0);
            String planName = activeSub.getPlan().getName();
            if (planName.equalsIgnoreCase("Student") || planName.equalsIgnoreCase("Free")) {
                return java.time.LocalDateTime.now().minusDays(7);
            } else {
                if (activeSub.getStartDate() == null) {
                    return java.time.LocalDateTime.now().minusDays(7);
                }
                return activeSub.getStartDate().minusDays(7);
            }
        }
        return java.time.LocalDateTime.now().minusDays(7);
    }

    private ChatSession getSessionEntity(String email, Long sessionId) {
        User user = getUserByEmail(email);
        ChatSession session = chatSessionRepository.findByIdAndUserIdAndIsActiveTrue(sessionId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Chat Session not found or deleted"));

        java.time.LocalDateTime cutoff = getHistoryCutoffDate(user.getId());
        if (session.getCreatedAt().isBefore(cutoff)) {
            throw new RuntimeException("Gói hiện tại của bạn không cho phép xem lịch sử chat trước thời điểm " + cutoff.toLocalDate().toString() + ".");
        }
        return session;
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

    @Override
    @Transactional(readOnly = true)
    public Object prepareRegen(String email, Long sessionId, String errorIndices) {
        User user = getUserByEmail(email);
        ChatSession session = getSessionEntity(email, sessionId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("session_id", sessionId.toString());
        if (errorIndices != null) body.add("error_indices", errorIndices);

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        String targetUrl = aiServerUrl.endsWith("/") ? aiServerUrl + "prepare-regen" : aiServerUrl + "/prepare-regen";
        ResponseEntity<String> responseEntity = restTemplate.postForEntity(targetUrl, entity, String.class);

        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readTree(responseEntity.getBody());
        } catch (Exception e) {
            return responseEntity.getBody();
        }
    }

    @Override
    @Transactional
    public Object regenImage(String email, Long sessionId, String errorIndices, String finalPrompt) {
        User user = getUserByEmail(email);
        ChatSession session = getSessionEntity(email, sessionId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("session_id", sessionId.toString());
        if (errorIndices != null) body.add("error_indices", errorIndices);
        if (finalPrompt != null) body.add("final_prompt", finalPrompt);

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        String targetUrl = aiServerUrl.endsWith("/") ? aiServerUrl + "regen-image" : aiServerUrl + "/regen-image";
        ResponseEntity<String> responseEntity = restTemplate.postForEntity(targetUrl, entity, String.class);
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode resultNode = mapper.readTree(responseEntity.getBody());
            
            // Log as AI response in DB if user generated it
            // if we actually want to save regen results as messages
            ChatMessage aiMessage = ChatMessage.builder()
                .session(session)
                .role(MessageRole.AI)
                .content(resultNode.toString())
                .tokensUsed(0) // grok tokens are not calculated yet on BE
                .build();
            chatMessageRepository.save(aiMessage);
            
            return resultNode;
        } catch (Exception e) {
            return responseEntity.getBody();
        }
    }

    @Override
    @Transactional
    public Object suggestStyle(String email, MultipartFile file, String box2d, String suggestType) {
        User user = getUserByEmail(email);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        try {
            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename() != null ? file.getOriginalFilename() : "image.png";
                }
            };
            body.add("file", fileResource);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi đọc file: " + e.getMessage());
        }

        if (box2d != null) body.add("box_2d", box2d);
        if (suggestType != null) body.add("suggest_type", suggestType);

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        String targetUrl = aiServerUrl.endsWith("/") ? aiServerUrl + "api/suggest-style" : aiServerUrl + "/api/suggest-style";
        
        ResponseEntity<String> responseEntity = restTemplate.postForEntity(targetUrl, entity, String.class);
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readTree(responseEntity.getBody());
        } catch (Exception e) {
            return responseEntity.getBody();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Object extractLayers(String email, String imageBase64, String mimeType, int numLayers) {
        getUserByEmail(email);

        if (imageBase64 == null || imageBase64.isBlank()) {
            throw new IllegalArgumentException("image_base64 is required");
        }
        if (falKey == null || falKey.isBlank()) {
            throw new IllegalArgumentException("FAL_KEY chưa được cấu hình trên server. Thêm FAL_KEY vào file .env của backend.");
        }

        String normalizedBase64 = imageBase64.trim();
        if (normalizedBase64.contains(",")) {
            normalizedBase64 = normalizedBase64.substring(normalizedBase64.indexOf(',') + 1);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> reqBody = new HashMap<>();
        reqBody.put("image_base64", normalizedBase64);
        reqBody.put("mime_type", mimeType != null && !mimeType.isBlank() ? mimeType : "image/png");
        reqBody.put("api_key", falKey);
        reqBody.put("num_layers", numLayers > 0 ? numLayers : 5);
        reqBody.put("guidance_scale", 5.0);
        reqBody.put("num_inference_steps", 30);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(reqBody, headers);
        String targetUrl = aiServerUrl.endsWith("/")
                ? aiServerUrl + "extract-layers"
                : aiServerUrl + "/extract-layers";

        try {
            ResponseEntity<String> responseEntity = restTemplate.postForEntity(targetUrl, entity, String.class);
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readTree(responseEntity.getBody());
        } catch (HttpStatusCodeException e) {
            String body = e.getResponseBodyAsString();
            throw new RuntimeException(
                    body != null && !body.isBlank() ? body : "AI server error: " + e.getStatusCode());
        } catch (ResourceAccessException e) {
            throw new RuntimeException("Không kết nối được AI server tại " + targetUrl + ": " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Tách layer thất bại: " + e.getMessage());
        }
    }
}
