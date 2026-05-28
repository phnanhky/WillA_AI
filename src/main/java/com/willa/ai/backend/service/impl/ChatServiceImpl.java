package com.willa.ai.backend.service.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
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
import com.willa.ai.backend.util.UploadSizeValidator;
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
import com.willa.ai.backend.entity.enums.WorkflowType;
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
import com.willa.ai.backend.service.WorkflowUsageService;

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
    private final WorkflowUsageService workflowUsageService;
    private final UploadSizeValidator uploadSizeValidator;
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
        WorkflowType workflow = hasAnalyzableUpload(files) ? WorkflowType.ANALYZE : WorkflowType.CHAT;
        return workflowUsageService.track(user, workflow, sessionId,
                () -> doSendMessageToAi(user, email, sessionId, content, actionType, errorIndex, box2d, files));
    }

    private ChatMessageResponse doSendMessageToAi(User user, String email, Long sessionId, String content, String actionType, Integer errorIndex, String box2d, List<MultipartFile> files) {
        String planName = getPlanNameForUser(user.getId());
        if (Boolean.TRUE.equals(user.getRequiresReview()) && planName.equalsIgnoreCase("Free")) {
            throw new RuntimeException("Bạn cần để lại đánh giá (Review) trước khi tiếp tục ở gói Free.");
        }

        Wallet wallet = walletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Wallet not found"));
        if (wallet.getTokenBalance() <= 0) {
            throw new RuntimeException("Số dư token của bạn đã hết. Vui lòng nạp thêm hoặc nâng cấp gói để tiếp tục.");
        }

        ChatSession session = getSessionEntity(email, sessionId);
        String sessionKey = sessionId.toString();
        String aiResponseContent;
        int totalTokensCombined = 0;
        CompletableFuture<String> imageUrlsFuture = CompletableFuture.completedFuture(null);

        try {
            List<ImagePart> images = expandToImageParts(files);

            // Phân quyền theo gói: chỉ gói PRO mới được gửi file PDF/PSD hoặc nhiều ảnh.
            // Gói Student/Free chỉ phân tích đúng 1 ảnh thường mỗi lần.
            if (!planName.equalsIgnoreCase("Pro")) {
                long uploadCount = files == null ? 0
                        : files.stream().filter(f -> f != null && !f.isEmpty()).count();
                boolean hasDocumentFile = files != null && files.stream()
                        .filter(f -> f != null && !f.isEmpty())
                        .anyMatch(f -> {
                            String n = f.getOriginalFilename() != null
                                    ? f.getOriginalFilename().toLowerCase() : "";
                            return n.endsWith(".pdf") || n.endsWith(".psd");
                        });
                if (uploadCount > 1 || hasDocumentFile || images.size() > 1) {
                    throw new RuntimeException("Gói " + planName
                            + " chỉ hỗ trợ phân tích 1 ảnh mỗi lần. "
                            + "Vui lòng nâng cấp lên gói Pro để gửi file PDF hoặc nhiều ảnh.");
                }
            }

            if (images.isEmpty()) {
                MultiValueMap<String, Object> body = aiServerClient.chatForm(sessionKey, content, actionType, errorIndex, box2d);
                JsonNode rootNode = AiAnalysisEnricher.enrich(aiServerClient.chat(body));
                aiResponseContent = rootNode.toString();
                totalTokensCombined = deductTokensForAiCall(user, wallet, rootNode, "CHAT");
            } else {
                final List<ImagePart> imagesForUpload = images;
                imageUrlsFuture = CompletableFuture.supplyAsync(() -> uploadImagesToR2(imagesForUpload));
                com.fasterxml.jackson.databind.node.ArrayNode arrayNode = objectMapper.createArrayNode();
                for (ImagePart image : images) {
                    if (wallet.getTokenBalance() <= 0) {
                        break;
                    }
                    MultiValueMap<String, Object> body = aiServerClient.chatForm(sessionKey, content, actionType, errorIndex, box2d);
                    body.add("file", AiServerClient.toFileResource(image.bytes(), image.filename()));
                    JsonNode rootNode = AiAnalysisEnricher.enrich(aiServerClient.chat(body));
                    arrayNode.add(rootNode);
                    totalTokensCombined += deductTokensForAiCall(user, wallet, rootNode, "ANALYZE");
                }
                if (images.size() == 1 && arrayNode.size() > 0) {
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
     * Lưu ảnh preview lên R2 (song song với gọi AI). Ảnh &gt; 3MB được nén;
     * bytes gửi AI giữ nguyên trong {@link #sendMessageToAi}.
     */
    private String uploadImagesToR2(List<ImagePart> images) {
        List<String> uploadedUrls = new ArrayList<>();
        for (ImagePart image : images) {
            try {
                uploadedUrls.add(fileService.uploadBytes(
                        image.bytes(), image.filename(), image.contentType()));
            } catch (Exception e) {
                throw new RuntimeException("Lỗi upload ảnh: " + e.getMessage(), e);
            }
        }
        return uploadedUrls.isEmpty() ? null : String.join(",", uploadedUrls);
    }

    /**
     * Mở rộng danh sách file upload thành các ảnh để AI đọc:
     * <ul>
     *   <li>Ảnh thường: giữ nguyên</li>
     *   <li>PDF: render mỗi trang thành 1 ảnh PNG</li>
     *   <li>PSD: render thành 1 ảnh PNG</li>
     *   <li>CSV: bỏ qua (AI không đọc được như ảnh)</li>
     * </ul>
     * Đồng thời validate kích thước theo loại và tổng dung lượng request.
     */
    private List<ImagePart> expandToImageParts(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        List<ImagePart> images = new ArrayList<>();
        long totalBytes = 0;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String fn = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
            String baseName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "image";
            try {
                byte[] data = file.getBytes();
                uploadSizeValidator.validateByType(data.length, file.getOriginalFilename());
                totalBytes += data.length;
                uploadSizeValidator.validateRequestTotal(totalBytes);

                if (fn.endsWith(".pdf")) {
                    List<byte[]> pages = advancedFileParserService.renderPdfToPngBytes(file);
                    for (int i = 0; i < pages.size(); i++) {
                        images.add(new ImagePart(pages.get(i), baseName + "-p" + (i + 1) + ".png", "image/png"));
                    }
                } else if (fn.endsWith(".psd")) {
                    images.add(new ImagePart(advancedFileParserService.renderPsdToPngBytes(file), baseName + ".png", "image/png"));
                } else if (fn.endsWith(".csv")) {
                    // CSV không phải ảnh — bỏ qua, không gửi AI.
                    continue;
                } else {
                    images.add(new ImagePart(data, baseName, file.getContentType()));
                }
            } catch (IOException e) {
                throw new RuntimeException("Lỗi đọc file: " + e.getMessage(), e);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Lỗi phân tích file: " + e.getMessage(), e);
            }
        }
        return images;
    }

    private record ImagePart(byte[] bytes, String filename, String contentType) {}

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
        User user = getUserByEmail(email);
        return workflowUsageService.track(user, WorkflowType.PREPARE_REGEN, sessionId, () -> {
            getSessionEntity(email, sessionId);
            MultiValueMap<String, Object> body = aiServerClient.sessionForm(sessionId.toString());
            if (errorIndices != null) {
                body.add("error_indices", errorIndices);
            }
            return aiServerClient.prepareRegen(body);
        });
    }

    @Override
    @Transactional
    public Object regenImage(String email, Long sessionId, String errorIndices, String finalPrompt) {
        User user = getUserByEmail(email);
        return workflowUsageService.track(user, WorkflowType.REGEN, sessionId, () -> {
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
        });
    }

    @Override
    @Transactional
    public ChatMessageResponse generateImage(String email, Long sessionId, String prompt, List<MultipartFile> files) {
        User user = getUserByEmail(email);
        return workflowUsageService.track(user, WorkflowType.GENERATE, sessionId,
                () -> doGenerateImage(user, email, sessionId, prompt, files));
    }

    private ChatMessageResponse doGenerateImage(User user, String email, Long sessionId, String prompt, List<MultipartFile> files) {
        ChatSession session = getSessionEntity(email, sessionId);
        if (prompt == null || prompt.isBlank()) {
            throw new RuntimeException("Vui lòng nhập mô tả để tạo ảnh.");
        }

        Wallet wallet = walletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy ví của người dùng."));
        if (wallet.getTokenBalance() <= 0) {
            throw new RuntimeException("Insufficient token balance");
        }

        List<ImagePart> images = expandToImageParts(files);
        if (!images.isEmpty()) {
            return generateImageFromReference(email, user, session, sessionId, prompt, images, wallet);
        }

        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(userMsg);

        JsonNode result = aiServerClient.chatGenerate(messages);
        String rawReply = result.path("text").asText("");
        String storedImageUrl = storeGeneratedImageFromUrl(
                result.path("image_url").isNull() || result.path("image_url").isMissingNode()
                        ? null
                        : result.path("image_url").asText(null));
        String replyText = normalizeGenerateReply(
                rawReply, prompt, storedImageUrl != null && !storedImageUrl.isBlank());

        chatMessageRepository.save(ChatMessage.builder()
                .session(session)
                .role(MessageRole.USER)
                .content(prompt)
                .tokensUsed(0)
                .build());

        ChatMessage saved = chatMessageRepository.save(ChatMessage.builder()
                .session(session)
                .role(MessageRole.AI)
                .content(replyText)
                .imageUrl(storedImageUrl)
                .tokensUsed(0)
                .build());
        return mapToMessageResponse(saved);
    }

    /**
     * Image→image: seed session on AI server via /chat upload, then /regen-image with empty
     * error indices and the user's prompt (InpaintAgent — no AI server code changes).
     */
    private ChatMessageResponse generateImageFromReference(
            String email,
            User user,
            ChatSession session,
            Long sessionId,
            String prompt,
            List<ImagePart> images,
            Wallet wallet) {
        String planName = getPlanNameForUser(user.getId());
        if (!planName.equalsIgnoreCase("Pro") && images.size() > 1) {
            throw new RuntimeException("Gói " + planName
                    + " chỉ hỗ trợ tạo lại từ 1 ảnh tham chiếu. Nâng cấp Pro để gửi nhiều ảnh.");
        }

        String sessionKey = sessionId.toString();
        ImagePart reference = images.get(0);
        String userImageUrl = uploadImagesToR2(List.of(reference));

        try {
            MultiValueMap<String, Object> seedBody = aiServerClient.chatForm(sessionKey, prompt, null, null, null);
            seedBody.add("file", AiServerClient.toFileResource(reference.bytes(), reference.filename()));
            JsonNode seedNode = aiServerClient.chat(seedBody);
            deductTokensForAiCall(user, wallet, seedNode, "GENERATE_SEED");

            MultiValueMap<String, Object> regenBody = aiServerClient.sessionForm(sessionKey);
            regenBody.add("error_indices", "[]");
            regenBody.add("final_prompt", prompt);
            JsonNode regenNode = aiServerClient.regenImage(regenBody);

            String dataUrl = regenNode.path("image_data_url").asText(null);
            if (dataUrl == null || dataUrl.isBlank()) {
                dataUrl = regenNode.path("image_b64").asText(null);
            }
            String storedImageUrl = storeGeneratedImageFromDataUrl(dataUrl);
            if (storedImageUrl == null || storedImageUrl.isBlank()) {
                throw new RuntimeException("WillaAI không trả về ảnh. Vui lòng thử lại.");
            }
            String replyText = buildGenerateSuccessReply(prompt);

            chatMessageRepository.save(ChatMessage.builder()
                    .session(session)
                    .role(MessageRole.USER)
                    .content(prompt)
                    .imageUrl(userImageUrl)
                    .tokensUsed(0)
                    .build());

            ChatMessage saved = chatMessageRepository.save(ChatMessage.builder()
                    .session(session)
                    .role(MessageRole.AI)
                    .content(replyText)
                    .imageUrl(storedImageUrl)
                    .tokensUsed(0)
                    .build());
            return mapToMessageResponse(saved);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Không tạo lại được ảnh: " + e.getMessage(), e);
        }
    }

    private String storeGeneratedImageFromUrl(String externalImageUrl) {
        if (externalImageUrl == null || externalImageUrl.isBlank()) {
            return null;
        }
        byte[] bytes = aiServerClient.downloadBytes(externalImageUrl);
        if (bytes == null || bytes.length == 0) {
            return externalImageUrl;
        }
        try {
            return fileService.uploadBytes(
                    bytes, "generated-" + System.currentTimeMillis() + ".png", "image/png");
        } catch (Exception e) {
            return externalImageUrl;
        }
    }

    private String storeGeneratedImageFromDataUrl(String dataUrl) {
        byte[] bytes = decodeDataUrl(dataUrl);
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return fileService.uploadBytes(
                    bytes, "generated-" + System.currentTimeMillis() + ".png", "image/png");
        } catch (Exception e) {
            return dataUrl;
        }
    }

    /** Reply for Generate tool — never use regen-image "fixed N errors" wording. */
    private static String buildGenerateSuccessReply(String prompt) {
        return "✅ Successfully generated using WillaAI.";
    }

    private static boolean isRegenFixReply(String reply) {
        if (reply == null || reply.isBlank()) {
            return false;
        }
        String lower = reply.toLowerCase();
        return lower.contains("successfully fixed")
                || lower.contains("error regions")
                || lower.contains("improved design");
    }

    private static String normalizeGenerateReply(String rawReply, String prompt, boolean hasImage) {
        if (hasImage || isRegenFixReply(rawReply)) {
            return buildGenerateSuccessReply(prompt);
        }
        if (rawReply == null || rawReply.isBlank()) {
            return buildGenerateSuccessReply(prompt);
        }
        return rawReply;
    }

    private static byte[] decodeDataUrl(String dataUrl) {
        if (dataUrl == null || dataUrl.isBlank()) {
            return null;
        }
        String base64 = dataUrl.trim();
        int comma = base64.indexOf(',');
        if (comma >= 0) {
            base64 = base64.substring(comma + 1);
        }
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    @Transactional
    public Object suggestStyle(String email, MultipartFile file, String box2d, String suggestType) {
        User user = getUserByEmail(email);
        return workflowUsageService.track(user, WorkflowType.SUGGEST_STYLE, null, () -> {
            uploadSizeValidator.validateImage(file);
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", AiServerClient.toFileResource(file));
            body.add("box_2d", box2d != null ? box2d : "[]");
            body.add("suggest_type", suggestType != null ? suggestType : "typo");
            return aiServerClient.suggestStyle(body);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Object extractLayers(String email, String imageBase64, String mimeType, int numLayers) {
        User user = getUserByEmail(email);
        return workflowUsageService.track(user, WorkflowType.EXTRACT_LAYERS, null, () -> {
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
        });
    }

    private static boolean hasAnalyzableUpload(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return false;
        }
        return files.stream().anyMatch(f -> {
            if (f == null || f.isEmpty()) {
                return false;
            }
            String fn = f.getOriginalFilename() != null ? f.getOriginalFilename().toLowerCase() : "";
            return !fn.endsWith(".csv");
        });
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
