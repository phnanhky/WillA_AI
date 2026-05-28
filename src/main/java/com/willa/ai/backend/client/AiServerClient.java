package com.willa.ai.backend.client;

import java.util.Map;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.willa.ai.backend.config.AiServerProperties;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AiServerClient {

    private final RestTemplate restTemplate;
    private final AiServerProperties aiServer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonNode chat(MultiValueMap<String, Object> form) {
        return postMultipart(aiServer.chatUrl(), form);
    }

    public JsonNode prepareRegen(MultiValueMap<String, Object> form) {
        return postMultipart(aiServer.prepareRegenUrl(), form);
    }

    public JsonNode regenImage(MultiValueMap<String, Object> form) {
        return postMultipart(aiServer.regenImageUrl(), form);
    }

    public JsonNode suggestStyle(MultiValueMap<String, Object> form) {
        return postMultipart(aiServer.suggestStyleUrl(), form);
    }

    public JsonNode extractLayers(Map<String, Object> body) {
        return postJson(aiServer.extractLayersUrl(), body);
    }

    public JsonNode health() {
        ResponseEntity<String> response = restTemplate.getForEntity(aiServer.healthUrl(), String.class);
        return parseBody(response.getBody());
    }

    public MultiValueMap<String, Object> chatForm(
            String sessionId,
            String message,
            String actionType,
            Integer errorIndex,
            String box2d) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        if (message != null && !message.trim().isEmpty()) {
            body.add("message", message);
        }
        body.add("session_id", sessionId);
        if (actionType != null && !actionType.trim().isEmpty()) {
            body.add("action_type", actionType);
        }
        if (errorIndex != null) {
            body.add("error_index", errorIndex.toString());
        }
        if (box2d != null && !box2d.trim().isEmpty()) {
            body.add("box_2d", box2d);
        }
        return body;
    }

    public MultiValueMap<String, Object> sessionForm(String sessionId) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("session_id", sessionId);
        return body;
    }

    /** Gửi file gốc lên AI (không nén — nén chỉ áp dụng khi lưu R2). */
    public static ByteArrayResource toFileResource(MultipartFile file) {
        try {
            return toFileResource(file.getBytes(), file.getOriginalFilename());
        } catch (Exception e) {
            throw new RuntimeException("Lỗi đọc file: " + e.getMessage(), e);
        }
    }

    public static ByteArrayResource toFileResource(byte[] data, String originalFilename) {
        String name = originalFilename != null && !originalFilename.isBlank() ? originalFilename : "image";
        return new ByteArrayResource(data) {
            @Override
            public String getFilename() {
                return name;
            }
        };
    }

    public TokenUsage parseUsage(JsonNode root) {
        if (root == null || root.isMissingNode()) {
            return TokenUsage.empty();
        }
        JsonNode usage = root.get("usage");
        if (usage != null && usage.isObject()) {
            int input = usage.path("input_tokens").asInt(0);
            int output = usage.path("output_tokens").asInt(0);
            int total = usage.path("total_tokens").asInt(input + output);
            if (total > 0 || input > 0 || output > 0) {
                return new TokenUsage(input, output, total > 0 ? total : input + output);
            }
        }
        JsonNode analysis = root.get("analysis_data");
        if (analysis != null && analysis.isObject()) {
            int input = analysis.path("inputtoken").asInt(0);
            int output = analysis.path("outputtoken").asInt(0);
            int total = analysis.path("totaltoken").asInt(input + output);
            if (total > 0 || input > 0 || output > 0) {
                return new TokenUsage(input, output, total > 0 ? total : input + output);
            }
        }
        return TokenUsage.empty();
    }

    private JsonNode postMultipart(String url, MultiValueMap<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        return exchange(url, entity);
    }

    private JsonNode postJson(String url, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        return exchange(url, entity);
    }

    private JsonNode exchange(String url, HttpEntity<?> entity) {
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            return parseBody(response.getBody());
        } catch (HttpStatusCodeException e) {
            String detail = e.getResponseBodyAsString();
            throw new RuntimeException(
                    detail != null && !detail.isBlank() ? detail : "AI server error: " + e.getStatusCode(),
                    e);
        } catch (ResourceAccessException e) {
            throw new RuntimeException("Không kết nối được AI server tại " + url + ": " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("AI server request failed: " + e.getMessage(), e);
        }
    }

    private JsonNode parseBody(String body) {
        if (body == null || body.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new RuntimeException("Invalid JSON from AI server: " + e.getMessage(), e);
        }
    }

    @Getter
    public static final class TokenUsage {
        private final int promptTokens;
        private final int completionTokens;
        private final int totalTokens;

        public TokenUsage(int promptTokens, int completionTokens, int totalTokens) {
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.totalTokens = totalTokens;
        }

        public static TokenUsage empty() {
            return new TokenUsage(0, 0, 0);
        }

        public boolean hasTokens() {
            return totalTokens > 0;
        }

        public TokenUsage plus(TokenUsage other) {
            if (other == null) {
                return this;
            }
            return new TokenUsage(
                    promptTokens + other.promptTokens,
                    completionTokens + other.completionTokens,
                    totalTokens + other.totalTokens);
        }
    }
}
