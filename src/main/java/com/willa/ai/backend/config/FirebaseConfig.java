package com.willa.ai.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Firebase Configuration for Google Authentication
 */
@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${firebase.project-id}")
    private String projectId;

    @Value("${firebase.private-key-id}")
    private String privateKeyId;

    @Value("${firebase.private-key}")
    private String privateKey;

    @Value("${firebase.client-email}")
    private String clientEmail;

    @Value("${firebase.client-id}")
    private String clientId;

    @Value("${firebase.auth-uri}")
    private String authUri;

    @Value("${firebase.token-uri}")
    private String tokenUri;

    @Value("${firebase.auth-provider-x509-cert-url}")
    private String authProviderCertUrl;

    @Value("${firebase.client-x509-cert-url}")
    private String clientCertUrl;

    public FirebaseConfig() {
    }

    @javax.annotation.PostConstruct
    public void init() {
        try {
            // Initialize Firebase with credentials from environment variables
            if (FirebaseApp.getApps().isEmpty()) {
                String serviceAccountJson = buildServiceAccountJson();
                
                GoogleCredentials credentials = GoogleCredentials.fromStream(
                        new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8))
                );

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(credentials)
                        .setProjectId(projectId)
                        .build();

                FirebaseApp.initializeApp(options);
                log.info("Firebase initialized successfully");
            }
        } catch (IOException e) {
            log.error("Error initializing Firebase: {}", e.getMessage(), e);
            // Don't throw - let the application start even if Firebase fails
        }
    }

    private String buildServiceAccountJson() {
        return "{\n" +
                "  \"type\": \"service_account\",\n" +
                "  \"project_id\": \"" + projectId + "\",\n" +
                "  \"private_key_id\": \"" + privateKeyId + "\",\n" +
                "  \"private_key\": \"" + privateKey.replace("\n", "\\n") + "\",\n" +
                "  \"client_email\": \"" + clientEmail + "\",\n" +
                "  \"client_id\": \"" + clientId + "\",\n" +
                "  \"auth_uri\": \"" + authUri + "\",\n" +
                "  \"token_uri\": \"" + tokenUri + "\",\n" +
                "  \"auth_provider_x509_cert_url\": \"" + authProviderCertUrl + "\",\n" +
                "  \"client_x509_cert_url\": \"" + clientCertUrl + "\",\n" +
                "  \"universe_domain\": \"googleapis.com\"\n" +
                "}";
    }
}
