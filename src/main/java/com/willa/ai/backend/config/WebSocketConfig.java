package com.willa.ai.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Topic broadcast cho những người chung 1 workspace
        // Ví dụ frontend subscribe vào: /topic/workspace/{workspaceId}
        config.enableSimpleBroker("/topic");
        
        // Client gửi message (kéo, thả, sửa font) lên BE bằng đường dẫn có prefix này
        // Ví dụ: /app/workspace.edit
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Gateway kết nối ban đầu
        registry.addEndpoint("/ws-design")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
