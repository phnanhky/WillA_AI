package com.willa.ai.backend.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.willa.ai.backend.dto.response.WorkspaceChatMessageResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceRealtimeService {

    private final SimpMessagingTemplate messagingTemplate;

    public void publishWorkspaceChanged(Long workspaceId) {
        publish(workspaceId, Map.of(
                "type", "WORKSPACE_CHANGED",
                "workspaceId", workspaceId));
    }

    public void publishChannelChanged(Long workspaceId, String eventType, Long channelId) {
        publish(workspaceId, Map.of(
                "type", eventType,
                "workspaceId", workspaceId,
                "channelId", channelId));
    }

    public void publishChannelMessage(Long workspaceId, Long channelId, WorkspaceChatMessageResponse message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "CHANNEL_MESSAGE_CREATED");
        payload.put("workspaceId", workspaceId);
        payload.put("channelId", channelId);
        payload.put("message", message);
        publish(workspaceId, payload);
    }

    public void publishDmMessage(Long workspaceId, Long conversationId, Long peerUserId,
            WorkspaceChatMessageResponse message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "DM_MESSAGE_CREATED");
        payload.put("workspaceId", workspaceId);
        payload.put("conversationId", conversationId);
        payload.put("peerUserId", peerUserId);
        payload.put("message", message);
        publish(workspaceId, payload);
    }

    private void publish(Long workspaceId, Map<String, Object> payload) {
        messagingTemplate.convertAndSend("/topic/workspace/" + workspaceId, payload);
    }
}
