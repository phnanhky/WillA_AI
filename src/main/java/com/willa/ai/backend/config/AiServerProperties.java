package com.willa.ai.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;

/**
 * Base URL and paths for qwenv3vagenAI (FastAPI, port 8000).
 * {@code AI_SERVER_URL} must be the server root, e.g. {@code http://ai-server:8000},
 * not OpenAI-style {@code /v1/chat/completions}.
 */
@Component
@ConfigurationProperties(prefix = "ai.server")
@Getter
@Setter
public class AiServerProperties {

    /** Raw value from env (may include legacy suffix). */
    private String url = "http://localhost:8000";

    private String estimate = "/estimate";
    private String chat = "/chat";
    private String prepareRegen = "/prepare-regen";
    private String regenImage = "/regen-image";
    private String seedAnalysis = "/seed-analysis";
    private String suggestStyle = "/api/suggest-style";
    private String extractLayers = "/extract-layers";
    private String chatGenerate = "/chat-generate";
    private String health = "/health";

    private String baseUrl;

    @PostConstruct
    void normalizeBaseUrl() {
        String normalized = url == null ? "" : url.trim();
        if (normalized.endsWith("/v1/chat/completions")) {
            normalized = normalized.substring(0, normalized.length() - "/v1/chat/completions".length());
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isEmpty()) {
            normalized = "http://localhost:8000";
        }
        this.baseUrl = normalized;
    }

    public String endpoint(String path) {
        String p = path.startsWith("/") ? path : "/" + path;
        return baseUrl + p;
    }

    public String estimateUrl() {
        return endpoint(estimate);
    }

    public String chatUrl() {
        return endpoint(chat);
    }

    public String prepareRegenUrl() {
        return endpoint(prepareRegen);
    }

    public String regenImageUrl() {
        return endpoint(regenImage);
    }

    public String seedAnalysisUrl() {
        return endpoint(seedAnalysis);
    }

    public String suggestStyleUrl() {
        return endpoint(suggestStyle);
    }

    public String extractLayersUrl() {
        return endpoint(extractLayers);
    }

    public String chatGenerateUrl() {
        return endpoint(chatGenerate);
    }

    public String healthUrl() {
        return endpoint(health);
    }
}
