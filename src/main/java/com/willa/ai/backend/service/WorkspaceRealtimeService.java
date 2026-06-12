package com.willa.ai.backend.service;

import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceRealtimeService {

    private final SimpMessagingTemplate messagingTemplate;

    public void publishWorkspaceChanged(Long workspaceId) {
        messagingTemplate.convertAndSend(
                "/topic/workspace/" + workspaceId,
                Map.of(
                        "type", "WORKSPACE_CHANGED",
                        "workspaceId", workspaceId));
    }
}
