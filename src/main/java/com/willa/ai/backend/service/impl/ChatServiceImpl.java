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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.willa.ai.backend.client.AiServerClient;
import com.willa.ai.backend.client.AiServerClient.TokenUsage;
import com.willa.ai.backend.config.QwenTokenEstimateProperties;
import com.willa.ai.backend.util.QwenVisionTokenMath;
import com.willa.ai.backend.util.UploadSizeValidator;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import com.willa.ai.backend.dto.response.QwenTokenEstimateResponse;
import com.willa.ai.backend.service.ChatService;
import com.willa.ai.backend.service.FileService;
import com.willa.ai.backend.service.PersonaService;
import com.willa.ai.backend.service.QwenTokenEstimateService;
import com.willa.ai.backend.service.QwenTokenEstimateService.ImageEstimateInput;
import com.willa.ai.backend.service.QwenTokenEstimateService.TextChatEstimateContext;
import com.willa.ai.backend.service.WalletService;
import com.willa.ai.backend.service.WorkflowUsageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatServiceImpl implements ChatService {
    private static final String AI_PAYLOAD_SCHEMA_VERSION = "1.0.0";

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
    private final QwenTokenEstimateService qwenTokenEstimateService;
    private final QwenTokenEstimateProperties qwenTokenEstimateProperties;
    private final PersonaService personaService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai.qwen.model:qwen3-vl-flash}")
    private String qwenModel;

    @Value("${ai.fal.key:}")
    private String falKey;

    @Autowired
    private AdvancedFileParserService advancedFileParserService;

    @Autowired
    private com.willa.ai.backend.repository.WorkflowUsageRepository workflowUsageRepository;

    @Autowired
    private GenerationLimitChecker generationLimitChecker;

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
    public ChatMessageResponse sendMessageToAi(String email, Long sessionId, String content, String actionType, Integer errorIndex, String box2d, Integer imageIndex, String replyLang, List<MultipartFile> files) {
        User user = getUserByEmail(email);
        WorkflowType workflow = hasAnalyzableUpload(files) ? WorkflowType.ANALYZE : WorkflowType.CHAT;
        return workflowUsageService.track(user, workflow, sessionId,
                () -> doSendMessageToAi(user, email, sessionId, content, actionType, errorIndex, box2d, imageIndex, replyLang, files));
    }

    private ChatMessageResponse doSendMessageToAi(User user, String email, Long sessionId, String content, String actionType, Integer errorIndex, String box2d, Integer imageIndex, String replyLang, List<MultipartFile> files) {
        String planName = getPlanNameForUser(user.getId());
        if (Boolean.TRUE.equals(user.getRequiresReview()) && planName.equalsIgnoreCase("Free")) {
            throw new RuntimeException("Bạn cần để lại đánh giá (Review) trước khi tiếp tục ở gói Free.");
        }

        Wallet wallet = walletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Wallet not found"));

        ChatSession session = getSessionEntity(email, sessionId);
        String sessionKey = sessionId.toString();
        String aiResponseContent;
        int totalTokensCombined = 0;
        CompletableFuture<String> imageUrlsFuture = CompletableFuture.completedFuture(null);

        try {
            List<ImagePart> images = expandToImageParts(files);

            String personaContext = personaService.getAiContextJsonForUser(user.getId());
            QwenTokenEstimateResponse estimate = estimateTokenUsage(images, content, personaContext, sessionId);
            long walletBefore = wallet.getTokenBalance() != null ? wallet.getTokenBalance() : 0L;
            log.info(
                    "Token ESTIMATE [userId={}, sessionId={}, model={}, images={}]: input={}, output={}, total={}, wallet={} ({})",
                    user.getId(), sessionId, qwenModel, estimate.getImageCount(),
                    estimate.getInputTokens(), estimate.getOutputTokens(), estimate.getTotalTokens(), walletBefore,
                    qwenTokenEstimateProperties.isUseAiServer() ? "ai-server" : "local");
            qwenTokenEstimateService.requireSufficientBalance(wallet, estimate.getTotalTokens());

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

            int actualInput = 0;
            int actualOutput = 0;
            if (images.isEmpty()) {
                if ("zoom".equalsIgnoreCase(actionType)) {
                    seedAiAnalysisFromSession(email, sessionId, imageIndex);
                }
                MultiValueMap<String, Object> body = aiServerClient.chatForm(
                        sessionKey, content, actionType, errorIndex, box2d, personaContext, replyLang);
                JsonNode rootNode = callAiChatWithAnalysisRecovery(email, sessionId, body);
                aiResponseContent = contentFromAiNode(rootNode);
                TokenUsage usage = deductTokensForAiCall(user, wallet, rootNode, "CHAT");
                totalTokensCombined = usage.getTotalTokens();
                actualInput = usage.getPromptTokens();
                actualOutput = usage.getCompletionTokens();
            } else {
                final List<ImagePart> imagesForUpload = images;
                imageUrlsFuture = CompletableFuture.supplyAsync(() -> uploadImagesToR2(imagesForUpload));
                com.fasterxml.jackson.databind.node.ArrayNode arrayNode = objectMapper.createArrayNode();
                int batchImageIndex = 0;
                for (ImagePart image : images) {
                    batchImageIndex++;
                    MultiValueMap<String, Object> body = aiServerClient.chatForm(
                            sessionKey, content, actionType, errorIndex, box2d, personaContext, replyLang);
                    body.add("file", AiServerClient.toFileResource(image.bytes(), image.filename()));
                    JsonNode rootNode = aiServerClient.chat(body);
                    JsonNode nodeToAdd = rootNode;
                    if (rootNode != null && rootNode.isObject()) {
                        ObjectNode copy = (ObjectNode) rootNode.deepCopy();
                        copy.put("image_index", batchImageIndex);
                        copy.put("image_total", images.size());
                        if (!copy.hasNonNull("schema_version")) {
                            copy.put("schema_version", AI_PAYLOAD_SCHEMA_VERSION);
                        }
                        nodeToAdd = copy;
                    }
                    arrayNode.add(nodeToAdd);
                    TokenUsage usage = deductTokensForAiCall(user, wallet, rootNode, "ANALYZE");
                    totalTokensCombined += usage.getTotalTokens();
                    actualInput += usage.getPromptTokens();
                    actualOutput += usage.getCompletionTokens();
                    log.info(
                            "Token ACTUAL per image [userId={}, sessionId={}, image {}/{}, file={}]: input={}, output={}, total={}",
                            user.getId(), sessionId, batchImageIndex, images.size(), image.filename(),
                            usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
                }
                if (images.size() == 1 && arrayNode.size() > 0) {
                    aiResponseContent = arrayNode.get(0).toString();
                } else {
                    aiResponseContent = arrayNode.toString();
                }
            }

            long walletAfter = wallet.getTokenBalance() != null ? wallet.getTokenBalance() : 0L;
            log.info(
                    "Token ACTUAL total [userId={}, sessionId={}]: input={}, output={}, total={}, walletAfter={} | vs estimate total={}",
                    user.getId(), sessionId, actualInput, actualOutput, totalTokensCombined, walletAfter,
                    estimate.getTotalTokens());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to call AI or parse response: " + e.getMessage(), e);
        }

        aiResponseContent = passthroughAiPayload(aiResponseContent);

        String imageUrlStr = imageUrlsFuture.join();

        ChatMessage userMessage = ChatMessage.builder()
                .session(session)
                .role(MessageRole.USER)
                .content(content)
                .imageUrl(imageUrlStr)
                .tokensUsed(0)
                .build();
        chatMessageRepository.save(userMessage);

        String storedAiContent = prepareAiContentForStorage(aiResponseContent);
        String aiMessageImageUrl = resolveAiMessageImageUrl(aiResponseContent, storedAiContent, imageUrlStr);

        ChatMessage aiMessage = ChatMessage.builder()
                .session(session)
                .role(MessageRole.AI)
                .content(storedAiContent)
                .imageUrl(aiMessageImageUrl)
                .tokensUsed(totalTokensCombined)
                .build();
        ChatMessage savedAiMessage = chatMessageRepository.save(aiMessage);
        if (isAnalysisPayload(aiResponseContent)) {
            personaService.refreshAfterAnalysis(user.getId());
        }
        return mapToMessageResponse(savedAiMessage);
    }

    private boolean isAnalysisPayload(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        if (content.contains("\"type\":\"analysis\"")
                || content.contains("\"type\": \"analysis\"")) {
            return true;
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            if (root.isObject()) {
                if ("analysis".equals(root.path("type").asText(""))) {
                    return true;
                }
                JsonNode ad = root.get("analysis_data");
                return ad != null && ad.isObject();
            }
            if (root.isArray()) {
                for (JsonNode n : root) {
                    if (n != null && n.isObject()) {
                        if ("analysis".equals(n.path("type").asText(""))) {
                            return true;
                        }
                        JsonNode ad = n.get("analysis_data");
                        if (ad != null && ad.isObject()) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Fallback to false.
        }
        return false;
    }

    private boolean isZoomPayload(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        return content.contains("\"type\":\"zoom\"")
                || content.contains("\"type\": \"zoom\"");
    }

    private boolean isRegenResultPayload(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        return content.contains("\"type\":\"regen_result\"")
                || content.contains("\"type\": \"regen_result\"");
    }

    private boolean isImagePayload(String content) {
        if (isZoomPayload(content) || isRegenResultPayload(content)) {
            return true;
        }
        if (content == null || content.isBlank()) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            if (!root.isObject()) {
                return false;
            }
            String dataUrl = root.path("image_data_url").asText("");
            String b64 = root.path("image_b64").asText("");
            return (dataUrl != null && !dataUrl.isBlank()) || (b64 != null && !b64.isBlank());
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Trả đúng JSON AI — không enrich/validate cứng (tránh lỗi xử lý box). */
    private String contentFromAiNode(JsonNode rootNode) {
        if (rootNode == null || rootNode.isNull()) {
            return "";
        }
        return rootNode.toString();
    }

    /**
     * Gắn {@code schema_version} nếu thiếu; không throw, không sửa analysis/box.
     */
    private String passthroughAiPayload(String aiResponseContent) {
        if (aiResponseContent == null || aiResponseContent.isBlank()) {
            return aiResponseContent != null ? aiResponseContent : "";
        }
        try {
            JsonNode root = objectMapper.readTree(aiResponseContent);
            if (root.isObject()) {
                ObjectNode obj = (ObjectNode) root.deepCopy();
                if (!obj.hasNonNull("schema_version")) {
                    obj.put("schema_version", AI_PAYLOAD_SCHEMA_VERSION);
                }
                return obj.toString();
            }
            if (root.isArray()) {
                com.fasterxml.jackson.databind.node.ArrayNode arr =
                        objectMapper.createArrayNode();
                int idx = 0;
                for (JsonNode n : root) {
                    idx++;
                    if (n != null && n.isObject()) {
                        ObjectNode o = (ObjectNode) n.deepCopy();
                        if (!o.hasNonNull("schema_version")) {
                            o.put("schema_version", AI_PAYLOAD_SCHEMA_VERSION);
                        }
                        if (!o.hasNonNull("image_index")) {
                            o.put("image_index", idx);
                        }
                        arr.add(o);
                    } else {
                        arr.add(n);
                    }
                }
                return arr.toString();
            }
            return root.toString();
        } catch (Exception e) {
            log.debug("passthroughAiPayload: keep raw AI string: {}", e.getMessage());
            return aiResponseContent;
        }
    }

    /**
     * Lưu đúng JSON AI. Chỉ thử upload ảnh zoom/regen lên R2 (best-effort); lỗi thì giữ JSON gốc.
     */
    private String prepareAiContentForStorage(String aiResponseContent) {
        if (aiResponseContent == null || aiResponseContent.isBlank()) {
            return aiResponseContent;
        }
        if (isImagePayload(aiResponseContent)) {
            return persistImagePayloadContent(aiResponseContent);
        }
        return aiResponseContent;
    }

    /** Upload base64 image in zoom / regen_result JSON to R2; keep full JSON for reload. */
    private String persistImagePayloadContent(String aiResponseContent) {
        try {
            JsonNode root = objectMapper.readTree(aiResponseContent);
            if (!root.isObject()) {
                return extractDisplayText(aiResponseContent, aiResponseContent);
            }
            String type = root.path("type").asText("");
            String dataUrl = root.path("image_data_url").asText(null);
            if (dataUrl == null || dataUrl.isBlank()) {
                dataUrl = root.path("image_b64").asText(null);
            }
            // Keep pure chat-only payloads as text. Keep any payload that carries image bytes/url.
            if ((dataUrl == null || dataUrl.isBlank())
                    && !"zoom".equals(type)
                    && !"regen_result".equals(type)) {
                return extractDisplayText(aiResponseContent, aiResponseContent);
            }
            ObjectNode out = ((ObjectNode) root).deepCopy();
            if (dataUrl != null && !dataUrl.isBlank()) {
                String stored = storeGeneratedImageFromDataUrl(dataUrl);
                if (stored != null && !stored.isBlank()) {
                    out.put("image_data_url", stored);
                    out.remove("image_b64");
                }
            }
            return out.toString();
        } catch (Exception e) {
            log.warn("persistImagePayloadContent failed, keep AI JSON: {}", e.getMessage());
            return aiResponseContent;
        }
    }

    private String resolveAiMessageImageUrl(String aiResponseContent, String storedAiContent, String uploadImageUrl) {
        if (isImagePayload(aiResponseContent)) {
            String imageUrl = extractImagePayloadUrl(storedAiContent);
            if (imageUrl != null && !imageUrl.isBlank()) {
                return imageUrl;
            }
        }
        return uploadImageUrl;
    }

    private String extractImagePayloadUrl(String storedContent) {
        if (storedContent == null || storedContent.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(storedContent);
            String url = root.path("image_data_url").asText(null);
            return url != null && !url.isBlank() ? url : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Chat / intent routing: chỉ trả text {@code reply} cho API & DB (không leak JSON nội bộ).
     */
    private String extractDisplayText(String raw, String fallback) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (root.isObject() && root.has("reply")) {
                String reply = root.path("reply").asText("");
                if (!reply.isBlank()) {
                    return reply;
                }
            }
        } catch (Exception e) {
            log.debug("extractDisplayText: {}", e.getMessage());
        }
        return fallback != null ? fallback : raw;
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

    private QwenTokenEstimateResponse estimateTokenUsage(
            List<ImagePart> images, String content, String personaContextJson, Long sessionId) {
        if (images.isEmpty()) {
            TextChatEstimateContext ctx = buildTextChatEstimateContext(sessionId);
            return qwenTokenEstimateService.estimateTextChat(content, personaContextJson, ctx);
        }
        return qwenTokenEstimateService.estimateFeedbackDesign(
                images.stream()
                        .map(img -> new ImageEstimateInput(img.bytes(), img.filename()))
                        .toList(),
                content,
                personaContextJson);
    }

    /**
     * Session đã từng phân tích ảnh → AI /chat không có file vẫn gọi intent routing (system lớn + history).
     */
    private TextChatEstimateContext buildTextChatEstimateContext(Long sessionId) {
        List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        boolean sessionHasImageContext = messages.stream()
                .anyMatch(m -> m.getRole() == MessageRole.AI && isAnalysisPayload(m.getContent()));

        int maxMessages = qwenTokenEstimateProperties.getChat().getHistoryMaxTurns() * 2;
        int start = Math.max(0, messages.size() - maxMessages);
        int historyTokens = 0;
        int historyCap = qwenTokenEstimateProperties.getChat().getRoutingHistoryCapTokens();
        for (int i = start; i < messages.size(); i++) {
            ChatMessage m = messages.get(i);
            String c = m.getContent();
            if (c == null || c.isBlank()) {
                continue;
            }
            // Payload phân tích đầy đủ không nằm trong history_messages AI — chỉ errors summary trong system.
            if (m.getRole() == MessageRole.AI && isAnalysisPayload(c)) {
                continue;
            }
            historyTokens += QwenVisionTokenMath.textTokens(c, qwenTokenEstimateProperties.getCharsPerToken());
        }
        if (historyCap > 0) {
            historyTokens = Math.min(historyTokens, historyCap);
        }

        int analysisTokens = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            if (m.getRole() == MessageRole.AI && isAnalysisPayload(m.getContent())) {
                analysisTokens = estimateAnalysisContextTokens(m.getContent());
                break;
            }
        }

        return new TextChatEstimateContext(sessionHasImageContext, historyTokens, analysisTokens);
    }

    /** Ước token phần errors/context nhúng trong system prompt intent (giống errors_context_str AI). */
    private int estimateAnalysisContextTokens(String analysisPayload) {
        var chat = qwenTokenEstimateProperties.getChat();
        int base = chat.getAnalysisErrorsBaseTokens();
        if (analysisPayload == null || analysisPayload.isBlank()) {
            return base;
        }
        try {
            JsonNode root = objectMapper.readTree(analysisPayload);
            JsonNode ad = root.path("analysis_data");
            if (ad.isMissingNode() || ad.isNull()) {
                ad = root;
            }
            JsonNode errors = ad.path("e");
            if (!errors.isArray() || errors.isEmpty()) {
                return base;
            }
            return base + errors.size() * chat.getAnalysisErrorsPerErrorTokens();
        } catch (Exception e) {
            log.debug("Could not parse analysis for token estimate: {}", e.getMessage());
            return base;
        }
    }

    private TokenUsage deductTokensForAiCall(User user, Wallet wallet, JsonNode rootNode, String serviceType) {
        TokenUsage usage = aiServerClient.parseUsage(rootNode);
        if (!usage.hasTokens()) {
            return usage;
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
        return usage;
    }

    @Override
    @Transactional(readOnly = true)
    public Object prepareRegen(String email, Long sessionId, String errorIndices, Integer imageIndex) {
        User user = getUserByEmail(email);
        return workflowUsageService.track(user, WorkflowType.PREPARE_REGEN, sessionId, () -> {
            getSessionEntity(email, sessionId);
            seedAiAnalysisFromSession(email, sessionId, imageIndex);
            MultiValueMap<String, Object> body = aiServerClient.sessionForm(sessionId.toString());
            if (errorIndices != null) {
                body.add("error_indices", errorIndices);
            }
            return callPrepareRegenWithAnalysisRecovery(email, sessionId, body);
        });
    }

    @Override
    @Transactional
    public Object regenImage(String email, Long sessionId, String errorIndices, String finalPrompt, Integer imageIndex) {
        User user = getUserByEmail(email);
        String planName = getPlanNameForUser(user.getId());
        generationLimitChecker.checkLimit(user, planName);
        return workflowUsageService.track(user, WorkflowType.REGEN, sessionId, () -> {
            ChatSession session = getSessionEntity(email, sessionId);
            seedAiAnalysisFromSession(email, sessionId, imageIndex);
            MultiValueMap<String, Object> body = aiServerClient.sessionForm(sessionId.toString());
            if (errorIndices != null) {
                body.add("error_indices", errorIndices);
            }
            if (finalPrompt != null) {
                body.add("final_prompt", finalPrompt);
            }
            JsonNode resultNode = callRegenImageWithAnalysisRecovery(email, sessionId, body);
            String rawJson = resultNode.toString();
            String storedContent = persistImagePayloadContent(rawJson);
            String regenImageUrl = extractImagePayloadUrl(storedContent);
            chatMessageRepository.save(ChatMessage.builder()
                    .session(session)
                    .role(MessageRole.AI)
                    .content(storedContent)
                    .imageUrl(regenImageUrl)
                    .tokensUsed(0)
                    .build());
            try {
                return objectMapper.readTree(storedContent);
            } catch (Exception e) {
                return resultNode;
            }
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

    private boolean isNeedAnalysisError(RuntimeException e) {
        String msg = e.getMessage();
        if (msg == null) {
            return false;
        }
        return msg.contains("Need to analyze")
                || msg.contains("analyze the image first")
                || msg.contains("before zooming");
    }

    private JsonNode callAiChatWithAnalysisRecovery(String email, Long sessionId, MultiValueMap<String, Object> body) {
        try {
            return aiServerClient.chat(body);
        } catch (RuntimeException e) {
            if (isNeedAnalysisError(e)) {
                seedAiAnalysisFromSession(email, sessionId, null);
                return aiServerClient.chat(body);
            }
            throw e;
        }
    }

    private JsonNode callPrepareRegenWithAnalysisRecovery(String email, Long sessionId, MultiValueMap<String, Object> body) {
        try {
            return aiServerClient.prepareRegen(body);
        } catch (RuntimeException e) {
            if (isNeedAnalysisError(e)) {
                seedAiAnalysisFromSession(email, sessionId, null);
                return aiServerClient.prepareRegen(body);
            }
            throw e;
        }
    }

    private JsonNode callRegenImageWithAnalysisRecovery(String email, Long sessionId, MultiValueMap<String, Object> body) {
        try {
            return aiServerClient.regenImage(body);
        } catch (RuntimeException e) {
            if (isNeedAnalysisError(e)) {
                seedAiAnalysisFromSession(email, sessionId, null);
                return aiServerClient.regenImage(body);
            }
            throw e;
        }
    }

    /**
     * AI server giữ phân tích trong RAM — sau restart hoặc mở session cũ cần nạp lại từ DB.
     */
    private void seedAiAnalysisFromSession(String email, Long sessionId, Integer imageIndex) {
        int idx = imageIndex != null && imageIndex >= 0 ? imageIndex : 0;
        ChatSession session = getSessionEntity(email, sessionId);
        List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());

        ChatMessage analysisMsg = null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            if (m.getRole() == MessageRole.AI && isAnalysisPayload(m.getContent())) {
                analysisMsg = m;
                break;
            }
        }
        if (analysisMsg == null) {
            throw new RuntimeException("Chưa có kết quả phân tích trong phiên chat. Vui lòng upload và phân tích ảnh trước.");
        }

        String imageUrl = analysisMsg.getImageUrl();
        if (imageUrl == null || imageUrl.isBlank()) {
            int msgIdx = messages.indexOf(analysisMsg);
            for (int i = msgIdx - 1; i >= 0; i--) {
                ChatMessage prev = messages.get(i);
                if (prev.getImageUrl() != null && !prev.getImageUrl().isBlank()) {
                    imageUrl = prev.getImageUrl();
                    break;
                }
            }
        }
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new RuntimeException("Không tìm thấy ảnh gốc của phiên phân tích.");
        }

        String[] urlParts = imageUrl.split(",");
        int urlIdx = Math.min(idx, urlParts.length - 1);
        if (urlIdx < 0) {
            urlIdx = 0;
        }
        String selectedImageUrl = urlParts[urlIdx].trim();
        byte[] imageBytes = loadImageBytesFromStoredUrl(selectedImageUrl);
        if (imageBytes == null || imageBytes.length == 0) {
            throw new RuntimeException(
                    "Không đọc được ảnh phân tích đã lưu. Vui lòng upload và phân tích lại ảnh.");
        }

        ObjectNode analysisResult;
        try {
            JsonNode root = objectMapper.readTree(analysisMsg.getContent());
            JsonNode analysisData = extractAnalysisDataNode(root, idx);
            if (analysisData == null || !analysisData.isObject()) {
                throw new RuntimeException("Dữ liệu phân tích không hợp lệ.");
            }
            analysisResult = ((ObjectNode) analysisData).deepCopy();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Không đọc được dữ liệu phân tích đã lưu: " + e.getMessage(), e);
        }

        String sessionKey = sessionId.toString();
        MultiValueMap<String, Object> seedBody = aiServerClient.sessionForm(sessionKey);
        seedBody.add("analysis_json", analysisResult.toString());
        seedBody.add("file", AiServerClient.toFileResource(imageBytes, "analysis.png"));
        aiServerClient.seedAnalysis(seedBody);
        log.info("Seeded AI analysis memory for sessionId={} imageIndex={} from DB (errors={})",
                sessionId, idx, analysisResult.path("e").size());
    }

    private JsonNode extractAnalysisDataNode(JsonNode root) {
        return extractAnalysisDataNode(root, 0);
    }

    private JsonNode extractAnalysisDataNode(JsonNode root, int imageIndex) {
        int idx = Math.max(0, imageIndex);
        if (root == null || root.isNull()) {
            return null;
        }
        if (root.isObject()) {
            JsonNode ad = root.get("analysis_data");
            return ad != null ? ad : root;
        }
        if (root.isArray()) {
            int analysisIdx = 0;
            for (int i = 0; i < root.size(); i++) {
                JsonNode item = root.get(i);
                if (item != null && item.isObject() && "analysis".equals(item.path("type").asText())) {
                    if (analysisIdx == idx) {
                        JsonNode ad = item.get("analysis_data");
                        return ad != null ? ad : item;
                    }
                    analysisIdx++;
                }
            }
            for (int i = root.size() - 1; i >= 0; i--) {
                JsonNode item = root.get(i);
                if (item != null && item.isObject() && "analysis".equals(item.path("type").asText())) {
                    JsonNode ad = item.get("analysis_data");
                    return ad != null ? ad : item;
                }
            }
        }
        return null;
    }

    /**
     * Ảnh trong DB thường là {@code APP_BASE_URL/api/files/download/uuid.ext}.
     * Trong Docker, HTTP tới {@code localhost} không tới R2 — đọc thẳng bucket qua {@link FileService}.
     */
    private byte[] loadImageBytesFromStoredUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        String fileKey = extractStoredFileKey(imageUrl.trim());
        if (fileKey != null) {
            try {
                byte[] fromR2 = fileService.downloadFile(fileKey);
                if (fromR2 != null && fromR2.length > 0) {
                    return fromR2;
                }
            } catch (RuntimeException e) {
                log.warn("R2 download failed for key {}: {}", fileKey, e.getMessage());
            }
        }
        byte[] viaHttp = aiServerClient.downloadBytes(imageUrl.trim());
        return viaHttp != null && viaHttp.length > 0 ? viaHttp : null;
    }

    private String extractStoredFileKey(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String path = url.trim();
        try {
            if (path.startsWith("http://") || path.startsWith("https://")) {
                path = java.net.URI.create(path).getPath();
            }
        } catch (Exception ignored) {
            // use raw url
        }
        String marker = "/api/files/download/";
        int idx = path.indexOf(marker);
        if (idx < 0) {
            return null;
        }
        String key = path.substring(idx + marker.length());
        int query = key.indexOf('?');
        if (query >= 0) {
            key = key.substring(0, query);
        }
        key = com.willa.ai.backend.service.impl.FileServiceImpl.decodeObjectKeyFromUrl(key);
        return key.isBlank() ? null : key;
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
        List<Subscription> activeSubs = subscriptionRepository.findActiveRecurringByUserId(
                userId, SubscriptionStatus.ACTIVE);
        if (!activeSubs.isEmpty()) {
            return activeSubs.get(0).getPlan().getName();
        }
        return "Free";
    }

    private java.time.LocalDateTime getHistoryCutoffDate(Long userId) {
        List<Subscription> activeSubs = subscriptionRepository.findActiveRecurringByUserId(
                userId, SubscriptionStatus.ACTIVE);
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
        String raw = message.getContent();
        Object contentObj;
        try {
            if (raw != null && message.getRole() == MessageRole.AI && !raw.isBlank()) {
                if (isAnalysisPayload(raw) || isImagePayload(raw)) {
                    contentObj = objectMapper.readTree(raw);
                } else {
                    contentObj = extractDisplayText(raw, raw);
                }
            } else if (raw != null && (raw.trim().startsWith("{") || raw.trim().startsWith("["))) {
                contentObj = objectMapper.readTree(raw);
            } else {
                contentObj = raw;
            }
        } catch (Exception e) {
            contentObj = raw;
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
