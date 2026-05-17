package com.willa.ai.backend.controller;

import com.willa.ai.backend.dto.websocket.DesignActionMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class DesignCollaborationController {

    private final SimpMessagingTemplate messagingTemplate;
    // Bắt các Event WebSocket từ FE bắn lên
    // Ví dụ FE gọi: stompClient.send("/app/workspace.1.edit", JSON.stringify(action))
    @MessageMapping("/workspace.{workspaceId}.edit")
    public void handleDesignAction(
            @DestinationVariable String workspaceId, 
            @Payload DesignActionMessage message) {
            
        log.info("Received Action [{}] from user [{}] for Workspace [{}] - Layer [{}]",
                message.getActionType(), message.getSenderEmail(), workspaceId, message.getLayerId());
                
        // (Optional) Tại đây, bạn check token JWT, lưu log vào DB, hoặc cập nhật nhanh vào Redis.
                
        // Broadcast (phát thanh) sự kiện này tới tất cả user khác đang mở chung 1 trang
        // Kênh mà tụi FE nó Subscribe lắng nghe sẽ là: /topic/workspace/{workspaceId}
        String topicDestination = "/topic/workspace/" + workspaceId;
        messagingTemplate.convertAndSend(topicDestination, message);
    }
}
