package com.willa.ai.backend.service;

import java.util.List;

import com.willa.ai.backend.dto.request.WorkspaceChannelRequest;
import com.willa.ai.backend.dto.request.WorkspaceChatMessageRequest;
import com.willa.ai.backend.dto.response.WorkspaceChannelResponse;
import com.willa.ai.backend.dto.response.WorkspaceChatMessageResponse;
import com.willa.ai.backend.entity.Workspace;
import com.willa.ai.backend.entity.WorkspaceChannel;

public interface WorkspaceChannelService {

    String WELCOME_CHANNEL_NAME = "Welcome";
    String GENERAL_CHANNEL_NAME = "General";

    void ensureDefaultChannels(Workspace workspace);

    void ensureWelcomeChannel(Workspace workspace);

    WorkspaceChannel ensureProjectChannel(Workspace workspace, String projectName);

    List<WorkspaceChannelResponse> listChannels(String email, Long workspaceId);

    WorkspaceChannelResponse createChannel(String email, Long workspaceId, WorkspaceChannelRequest request);

    WorkspaceChannelResponse updateChannel(String email, Long workspaceId, Long channelId, WorkspaceChannelRequest request);

    void deleteChannel(String email, Long workspaceId, Long channelId);

    List<WorkspaceChatMessageResponse> listChannelMessages(String email, Long workspaceId, Long channelId);

    List<WorkspaceChatMessageResponse> listChannelThreadReplies(String email, Long workspaceId, Long channelId);

    WorkspaceChatMessageResponse sendChannelMessage(String email, Long workspaceId, Long channelId,
            WorkspaceChatMessageRequest request);

    List<WorkspaceChatMessageResponse> listDmMessages(String email, Long workspaceId, Long peerUserId);

    WorkspaceChatMessageResponse sendDmMessage(String email, Long workspaceId, Long peerUserId,
            WorkspaceChatMessageRequest request);
}
