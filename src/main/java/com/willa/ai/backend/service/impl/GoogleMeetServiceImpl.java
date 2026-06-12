package com.willa.ai.backend.service.impl;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.willa.ai.backend.config.GoogleMeetProperties;
import com.willa.ai.backend.service.GoogleMeetService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleMeetServiceImpl implements GoogleMeetService {

    private static final String CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar";

    private final GoogleMeetProperties properties;
    private final ObjectMapper objectMapper;

    @Value("${firebase.project-id:}")
    private String firebaseProjectId;
    @Value("${firebase.private-key-id:}")
    private String firebasePrivateKeyId;
    @Value("${firebase.private-key:}")
    private String firebasePrivateKey;
    @Value("${firebase.client-email:}")
    private String firebaseClientEmail;
    @Value("${firebase.client-id:}")
    private String firebaseClientId;
    @Value("${firebase.auth-uri:}")
    private String firebaseAuthUri;
    @Value("${firebase.token-uri:}")
    private String firebaseTokenUri;
    @Value("${firebase.auth-provider-x509-cert-url:}")
    private String firebaseAuthProviderCertUrl;
    @Value("${firebase.client-x509-cert-url:}")
    private String firebaseClientCertUrl;

    @Override
    public String createMeetLink(String summary, String description) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Google Meet chưa được bật (GOOGLE_MEET_ENABLED=true)");
        }

        try {
            GoogleCredentials credentials = loadCredentials();
            credentials.refreshIfExpired();
            String accessToken = credentials.getAccessToken().getTokenValue();

            Instant start = Instant.now().plus(5, ChronoUnit.MINUTES);
            Instant end = start.plus(1, ChronoUnit.HOURS);
            String requestId = UUID.randomUUID().toString();

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("summary", summary != null && !summary.isBlank() ? summary : "Willa Meet");
            if (description != null && !description.isBlank()) {
                body.put("description", description);
            }
            body.put("start", Map.of("dateTime", start.toString(), "timeZone", "Asia/Ho_Chi_Minh"));
            body.put("end", Map.of("dateTime", end.toString(), "timeZone", "Asia/Ho_Chi_Minh"));
            body.put("conferenceData", Map.of(
                    "createRequest", Map.of(
                            "requestId", requestId,
                            "conferenceSolutionKey", Map.of("type", "hangoutsMeet"))));

            String json = objectMapper.writeValueAsString(body);
            String calendarId = properties.getCalendarId() != null ? properties.getCalendarId() : "primary";
            String url = "https://www.googleapis.com/calendar/v3/calendars/"
                    + java.net.URLEncoder.encode(calendarId, java.nio.charset.StandardCharsets.UTF_8)
                    + "/events?conferenceDataVersion=1";

            String responseBody = RestClient.create()
                    .post()
                    .uri(url)
                    .headers(h -> {
                        h.setBearerAuth(accessToken);
                        h.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .body(json)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode hangout = root.path("hangoutLink");
            if (!hangout.isMissingNode() && !hangout.asText().isBlank()) {
                return hangout.asText();
            }
            JsonNode entryPoints = root.path("conferenceData").path("entryPoints");
            if (entryPoints.isArray()) {
                for (JsonNode ep : entryPoints) {
                    if ("video".equals(ep.path("entryPointType").asText())) {
                        String uri = ep.path("uri").asText(null);
                        if (uri != null && !uri.isBlank()) {
                            return uri;
                        }
                    }
                }
            }
            throw new IllegalStateException("Google Calendar không trả về link Meet");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create Google Meet link", e);
            throw new IllegalStateException("Không tạo được link Google Meet: " + e.getMessage());
        }
    }

    private GoogleCredentials loadCredentials() throws Exception {
        GoogleCredentials credentials;
        String path = properties.getCredentialsPath();
        if (path != null && !path.isBlank()) {
            try (InputStream in = new FileInputStream(path)) {
                credentials = GoogleCredentials.fromStream(in).createScoped(CALENDAR_SCOPE);
            }
        } else if (firebaseClientEmail != null && !firebaseClientEmail.isBlank()
                && firebasePrivateKey != null && !firebasePrivateKey.isBlank()) {
            String json = buildServiceAccountJson();
            credentials = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)))
                    .createScoped(CALENDAR_SCOPE);
        } else {
            throw new IllegalStateException(
                    "Thiếu google.meet.credentials-path hoặc biến FIREBASE service account");
        }

        String impersonate = properties.getImpersonateEmail();
        if (impersonate != null && !impersonate.isBlank()
                && credentials instanceof ServiceAccountCredentials sac) {
            credentials = sac.createDelegated(impersonate);
        }
        return credentials;
    }

    private String buildServiceAccountJson() {
        return "{\n"
                + "  \"type\": \"service_account\",\n"
                + "  \"project_id\": \"" + firebaseProjectId + "\",\n"
                + "  \"private_key_id\": \"" + firebasePrivateKeyId + "\",\n"
                + "  \"private_key\": \"" + firebasePrivateKey.replace("\n", "\\n") + "\",\n"
                + "  \"client_email\": \"" + firebaseClientEmail + "\",\n"
                + "  \"client_id\": \"" + firebaseClientId + "\",\n"
                + "  \"auth_uri\": \"" + firebaseAuthUri + "\",\n"
                + "  \"token_uri\": \"" + firebaseTokenUri + "\",\n"
                + "  \"auth_provider_x509_cert_url\": \"" + firebaseAuthProviderCertUrl + "\",\n"
                + "  \"client_x509_cert_url\": \"" + firebaseClientCertUrl + "\",\n"
                + "  \"universe_domain\": \"googleapis.com\"\n"
                + "}";
    }
}
