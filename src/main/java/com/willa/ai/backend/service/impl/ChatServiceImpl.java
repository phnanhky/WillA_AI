package com.willa.ai.backend.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.willa.ai.backend.client.AiServerClient;
import com.willa.ai.backend.client.AiServerClient.TokenUsage;
import com.willa.ai.backend.util.AiAnalysisEnricher;
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
import org.springframework.security.core.userdetails.UsernameNotFoundException;

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
    private final AiServerClient aiServerClient;
    private final AITokenUsageRepository aiTokenUsageRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai.qwen.model:qwen3-vl-flash}")
    private String qwenModel;

    @Value("${ai.fal.key:}")
    private String falKey;

    @Autowired
    private AdvancedFileParserService advancedFileParserService;

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

    @Override
    @Transactional
    public ChatMessageResponse sendMessageToAi(String email, Long sessionId, String content, String actionType, Integer errorIndex, String box2d, List<MultipartFile> files) {
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
        String sessionKey = sessionId.toString();
        String aiResponseContent;
        int totalTokensCombined = 0;
        CompletableFuture<String> imageUrlsFuture = CompletableFuture.completedFuture(null);

        try {
            if (files == null || files.isEmpty()) {
                MultiValueMap<String, Object> body = aiServerClient.chatForm(sessionKey, content, actionType, errorIndex, box2d);
                JsonNode rootNode = AiAnalysisEnricher.enrich(aiServerClient.chat(body));
                aiResponseContent = rootNode.toString();
                totalTokensCombined = deductTokensForAiCall(user, wallet, rootNode, "CHAT");
            } else {
                imageUrlsFuture = CompletableFuture.supplyAsync(() -> resolveImageUrlsForChat(files));
                com.fasterxml.jackson.databind.node.ArrayNode arrayNode = objectMapper.createArrayNode();
                for (MultipartFile file : files) {
                    if (wallet.getTokenBalance() <= 0) {
                        break;
                    }
                    if (file == null || file.isEmpty()) {
                        continue;
                    }
                    MultiValueMap<String, Object> body = aiServerClient.chatForm(sessionKey, content, actionType, errorIndex, box2d);
                    body.add("file", AiServerClient.toFileResource(file));
                    JsonNode rootNode = AiAnalysisEnricher.enrich(aiServerClient.chat(body));
                    arrayNode.add(rootNode);
                    totalTokensCombined += deductTokensForAiCall(user, wallet, rootNode, "ANALYZE");
                }
                if (files.size() == 1 && arrayNode.size() > 0) {
                    aiResponseContent = arrayNode.get(0).toString();
                } else {
                    aiResponseContent = arrayNode.toString();
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to call AI or parse response: " + e.getMessage(), e);
        }

        String imageUrlStr = imageUrlsFuture.join();

        ChatMessage userMessage = ChatMessage.builder()
                .session(session)
                .role(MessageRole.USER)
                .content(content)
                .imageUrl(imageUrlStr)
                .tokensUsed(0)
                .build();
        chatMessageRepository.save(userMessage);

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

    /**
     * Upload ảnh lên R2 (chạy song song với {@link #sendMessageToAi} gọi AI).
     * PDF/PSD vẫn parse đồng bộ vì không gửi thẳng file ảnh thông thường.
     */
    private String resolveImageUrlsForChat(List<MultipartFile> files) {
        List<String> uploadedUrls = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String fn = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
            if (fn.endsWith(".pdf") || fn.endsWith(".csv") || fn.endsWith(".psd")) {
                try {
                    Map<String, Object> parseRest = advancedFileParserService.parseFile(file);
                    if ("pdf".equals(parseRest.get("type"))) {
                        @SuppressWarnings("unchecked")
                        List<String> base64Images = (List<String>) parseRest.get("pages");
                        uploadedUrls.addAll(base64Images);
                    } else if ("psd".equals(parseRest.get("type"))) {
                        uploadedUrls.add((String) parseRest.get("image"));
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Lỗi phân tích file đặc biệt: " + e.getMessage(), e);
                }
            } else {
                try {
                    uploadedUrls.add(fileService.uploadFile(file));
                } catch (Exception e) {
                    throw new RuntimeException("Lỗi upload ảnh: " + e.getMessage(), e);
                }
            }
        }
        return uploadedUrls.isEmpty() ? null : String.join(",", uploadedUrls);
    }

    private int deductTokensForAiCall(User user, Wallet wallet, JsonNode rootNode, String serviceType) {
        TokenUsage usage = aiServerClient.parseUsage(rootNode);
        if (!usage.hasTokens()) {
            return 0;
        }
        int total = usage.getTotalTokens();
        Long newBalance = wallet.getTokenBalance() - total;
        wallet.setTokenBalance(newBalance < 0 ? 0L : newBalance);
        walletRepository.save(wallet);
        aiTokenUsageRepository.save(AITokenUsage.builder()
                .user(user)
                .model(qwenModel)
                .promptTokens(usage.getPromptTokens())
                .completionTokens(usage.getCompletionTokens())
                .totalTokens(total)
                .serviceType(serviceType)
                .build());
        return total;
    }

    @Override
    @Transactional(readOnly = true)
    public Object prepareRegen(String email, Long sessionId, String errorIndices) {
        getSessionEntity(email, sessionId);
        MultiValueMap<String, Object> body = aiServerClient.sessionForm(sessionId.toString());
        if (errorIndices != null) {
            body.add("error_indices", errorIndices);
        }
        return aiServerClient.prepareRegen(body);
    }

    @Override
    @Transactional
    public Object regenImage(String email, Long sessionId, String errorIndices, String finalPrompt) {
        ChatSession session = getSessionEntity(email, sessionId);
        MultiValueMap<String, Object> body = aiServerClient.sessionForm(sessionId.toString());
        if (errorIndices != null) {
            body.add("error_indices", errorIndices);
        }
        if (finalPrompt != null) {
            body.add("final_prompt", finalPrompt);
        }
        JsonNode resultNode = aiServerClient.regenImage(body);
        chatMessageRepository.save(ChatMessage.builder()
                .session(session)
                .role(MessageRole.AI)
                .content(resultNode.toString())
                .tokensUsed(0)
                .build());
        return resultNode;
    }

    @Override
    @Transactional
    public Object suggestStyle(String email, MultipartFile file, String box2d, String suggestType) {
        getUserByEmail(email);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", AiServerClient.toFileResource(file));
        body.add("box_2d", box2d != null ? box2d : "[]");
        body.add("suggest_type", suggestType != null ? suggestType : "typo");
        return aiServerClient.suggestStyle(body);
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
        Map<String, Object> reqBody = new HashMap<>();
        reqBody.put("image_base64", normalizedBase64);
        reqBody.put("mime_type", mimeType != null && !mimeType.isBlank() ? mimeType : "image/png");
        reqBody.put("api_key", falKey);
        reqBody.put("num_layers", numLayers > 0 ? numLayers : 5);
        reqBody.put("guidance_scale", 5.0);
        reqBody.put("num_inference_steps", 30);
        reqBody.put("auto_detect", false);
        return aiServerClient.extractLayers(reqBody);
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
            }
            if (activeSub.getStartDate() == null) {
                return java.time.LocalDateTime.now().minusDays(7);
            }
            return activeSub.getStartDate().minusDays(7);
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
        Object contentObj;
        try {
            if (message.getContent() != null && (message.getContent().trim().startsWith("{") || message.getContent().trim().startsWith("["))) {
                contentObj = objectMapper.readTree(message.getContent());
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
