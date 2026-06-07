package com.willa.ai.backend.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.willa.ai.backend.document.GalleryItemDocument;
import com.willa.ai.backend.dto.response.GalleryItemResponse;
import com.willa.ai.backend.entity.ChatMessage;
import com.willa.ai.backend.entity.ChatSession;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.enums.MessageRole;
import com.willa.ai.backend.repository.GalleryItemElasticsearchRepository;
import com.willa.ai.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GallerySearchService {

    private final GalleryItemElasticsearchRepository galleryRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.gallery.elasticsearch.enabled:true}")
    private boolean enabled;

    public Page<GalleryItemResponse> search(String email, String query, int page, int size) {
        if (!enabled) {
            return Page.empty(PageRequest.of(page, size));
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Pageable pageable = PageRequest.of(page, size);
        String q = query == null ? "" : query.trim();

        try {
            Page<GalleryItemDocument> docs;
            if (q.isEmpty()) {
                docs = galleryRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable);
            } else {
                docs = galleryRepository.searchByUserIdAndQuery(user.getId(), q, pageable);
            }
            return docs.map(this::toResponse);
        } catch (Exception e) {
            log.warn("Gallery ES search failed for user {}: {}", user.getId(), e.getMessage());
            return Page.empty(pageable);
        }
    }

    public void indexGalleryMessage(Long userId, ChatSession session, ChatMessage message) {
        if (!enabled || message == null || session == null) {
            return;
        }
        if (!isGalleryEligibleMessage(message)) {
            return;
        }
        try {
            List<String> urls = splitImageUrls(message.getImageUrl());
            String description = parseDescription(message.getContent());
            String sessionTitle = session.getTitle() != null ? session.getTitle().trim() : "";
            Instant createdAt = message.getCreatedAt() != null
                    ? message.getCreatedAt().toInstant(ZoneOffset.UTC)
                    : Instant.now();

            List<GalleryItemDocument> docs = new ArrayList<>();
            for (int i = 0; i < urls.size(); i++) {
                String url = urls.get(i);
                String id = session.getId() + "-" + message.getId() + "-" + i;
                docs.add(GalleryItemDocument.builder()
                        .id(id)
                        .userId(userId)
                        .sessionId(session.getId())
                        .sessionTitle(sessionTitle)
                        .messageId(message.getId())
                        .imageUrl(url)
                        .description(description)
                        .createdAt(createdAt)
                        .build());
            }
            galleryRepository.saveAll(docs);
        } catch (Exception e) {
            log.warn("Failed to index gallery message {}: {}", message.getId(), e.getMessage());
        }
    }

    public void updateSessionTitleForUser(Long userId, Long sessionId, String newTitle) {
        if (!enabled || sessionId == null) {
            return;
        }
        try {
            Page<GalleryItemDocument> page = galleryRepository.findByUserIdOrderByCreatedAtDesc(
                    userId, PageRequest.of(0, 500));
            List<GalleryItemDocument> toUpdate = page.getContent().stream()
                    .filter(d -> sessionId.equals(d.getSessionId()))
                    .peek(d -> d.setSessionTitle(newTitle != null ? newTitle.trim() : ""))
                    .collect(Collectors.toList());
            if (!toUpdate.isEmpty()) {
                galleryRepository.saveAll(toUpdate);
            }
        } catch (Exception e) {
            log.warn("Failed to update gallery session title {}: {}", sessionId, e.getMessage());
        }
    }

    private GalleryItemResponse toResponse(GalleryItemDocument doc) {
        return GalleryItemResponse.builder()
                .id(doc.getId())
                .url(doc.getImageUrl())
                .sessionId(doc.getSessionId() != null ? doc.getSessionId().toString() : "")
                .resultMessageId(doc.getMessageId() != null ? doc.getMessageId().toString() : "")
                .sessionTitle(doc.getSessionTitle())
                .description(doc.getDescription())
                .createdAt(doc.getCreatedAt())
                .build();
    }

    private boolean isGalleryEligibleMessage(ChatMessage m) {
        if (m.getRole() != MessageRole.AI) {
            return false;
        }
        if (m.getImageUrl() == null || m.getImageUrl().isBlank()) {
            return false;
        }
        String content = m.getContent() != null ? m.getContent() : "";
        if (isZoomPayload(content) || isRegenResultPayload(content)) {
            return false;
        }
        if (isAnalysisPayload(content)) {
            return true;
        }
        return !content.trim().startsWith("{");
    }

    private boolean isAnalysisPayload(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        if (content.contains("\"type\":\"analysis\"") || content.contains("\"type\": \"analysis\"")) {
            return true;
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            if (root.isObject()) {
                return "analysis".equals(root.path("type").asText(""));
            }
            if (root.isArray()) {
                for (JsonNode n : root) {
                    if (n != null && n.isObject() && "analysis".equals(n.path("type").asText(""))) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
            // not JSON
        }
        return false;
    }

    private boolean isZoomPayload(String content) {
        return content != null && (content.contains("\"type\":\"zoom\"") || content.contains("\"type\": \"zoom\""));
    }

    private boolean isRegenResultPayload(String content) {
        return content != null
                && (content.contains("\"type\":\"regen_result\"") || content.contains("\"type\": \"regen_result\""));
    }

    private List<String> splitImageUrls(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        if (!raw.contains(",")) {
            return List.of(raw.trim());
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private String parseDescription(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            if (root.isObject()) {
                if ("analysis".equals(root.path("type").asText("")) && root.hasNonNull("reply")) {
                    return root.get("reply").asText();
                }
                if ("redesign".equals(root.path("type").asText("")) && root.hasNonNull("description")) {
                    return root.get("description").asText();
                }
            }
        } catch (Exception ignored) {
            // plain text
        }
        return content.length() > 500 ? content.substring(0, 500) : content;
    }
}
