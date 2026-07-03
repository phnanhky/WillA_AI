package com.willa.ai.backend.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.willa.ai.backend.dto.request.WorkspaceChannelRequest;
import com.willa.ai.backend.dto.request.WorkspaceChatMessageRequest;
import com.willa.ai.backend.dto.response.WorkspaceChannelResponse;
import com.willa.ai.backend.dto.response.WorkspaceChatMessageResponse;
import com.willa.ai.backend.entity.ChannelMessage;
import com.willa.ai.backend.entity.enums.ChannelMessageKind;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.Workspace;
import com.willa.ai.backend.entity.WorkspaceChannel;
import com.willa.ai.backend.entity.WorkspaceDmConversation;
import com.willa.ai.backend.entity.WorkspaceDmMessage;
import com.willa.ai.backend.repository.ChannelMessageRepository;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.repository.WorkspaceChannelRepository;
import com.willa.ai.backend.repository.WorkspaceDmConversationRepository;
import com.willa.ai.backend.repository.WorkspaceDmMessageRepository;
import com.willa.ai.backend.repository.WorkspaceMemberRepository;
import com.willa.ai.backend.repository.WorkspaceRepository;
import com.willa.ai.backend.service.WorkspaceChannelService;
import com.willa.ai.backend.service.WorkspaceRealtimeService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceChannelServiceImpl implements WorkspaceChannelService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceChannelRepository channelRepository;
    private final ChannelMessageRepository channelMessageRepository;
    private final WorkspaceDmConversationRepository dmConversationRepository;
    private final WorkspaceDmMessageRepository dmMessageRepository;
    private final UserRepository userRepository;
    private final WorkspaceRealtimeService workspaceRealtimeService;

    @Override
    @Transactional
    public void ensureDefaultChannels(Workspace workspace) {
        ensureWelcomeChannel(workspace);
        ensureGeneralChannel(workspace);
    }

    @Override
    @Transactional
    public void ensureWelcomeChannel(Workspace workspace) {
        if (channelRepository.findByWorkspaceIdAndNameIgnoreCase(workspace.getId(), WELCOME_CHANNEL_NAME).isPresent()) {
            return;
        }
        channelRepository.save(WorkspaceChannel.builder()
                .workspace(workspace)
                .name(WELCOME_CHANNEL_NAME)
                .position(0)
                .isSystem(true)
                .build());
    }

    private void ensureGeneralChannel(Workspace workspace) {
        if (channelRepository.findByWorkspaceIdAndNameIgnoreCase(workspace.getId(), GENERAL_CHANNEL_NAME).isPresent()) {
            return;
        }
        channelRepository.save(WorkspaceChannel.builder()
                .workspace(workspace)
                .name(GENERAL_CHANNEL_NAME)
                .position(1)
                .isSystem(true)
                .build());
    }

    @Override
    @Transactional
    public WorkspaceChannel ensureProjectChannel(Workspace workspace, String projectName) {
        return channelRepository.findByWorkspaceIdAndNameIgnoreCase(workspace.getId(), projectName)
                .orElseGet(() -> {
                    int nextPos = channelRepository.findByWorkspaceIdOrderByPositionAscIdAsc(workspace.getId()).size();
                    return channelRepository.save(WorkspaceChannel.builder()
                            .workspace(workspace)
                            .name(projectName)
                            .position(nextPos)
                            .isSystem(false)
                            .build());
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceChannelResponse> listChannels(String email, Long workspaceId) {
        User user = requireUser(email);
        assertIsMember(user, workspaceId);
        return channelRepository.findByWorkspaceIdOrderByPositionAscIdAsc(workspaceId).stream()
                .map(this::mapChannel)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public WorkspaceChannelResponse createChannel(String email, Long workspaceId, WorkspaceChannelRequest request) {
        User user = requireUser(email);
        assertIsMember(user, workspaceId);
        Workspace workspace = getWorkspaceOrThrow(workspaceId);
        String name = normalizeChannelName(request.getName());
        if (channelRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, name)) {
            throw new RuntimeException("Kênh \"" + name + "\" đã tồn tại");
        }
        int nextPos = channelRepository.findByWorkspaceIdOrderByPositionAscIdAsc(workspaceId).size();
        WorkspaceChannel channel = channelRepository.save(WorkspaceChannel.builder()
                .workspace(workspace)
                .name(name)
                .position(nextPos)
                .isSystem(false)
                .build());
        WorkspaceChannelResponse response = mapChannel(channel);
        workspaceRealtimeService.publishChannelChanged(workspaceId, "CHANNEL_CREATED", channel.getId());
        return response;
    }

    @Override
    @Transactional
    public WorkspaceChannelResponse updateChannel(String email, Long workspaceId, Long channelId,
            WorkspaceChannelRequest request) {
        User user = requireUser(email);
        assertIsMember(user, workspaceId);
        WorkspaceChannel channel = getChannelOrThrow(channelId, workspaceId);
        if (Boolean.TRUE.equals(channel.getIsSystem())) {
            throw new RuntimeException("Không thể đổi tên kênh hệ thống");
        }
        String name = normalizeChannelName(request.getName());
        channelRepository.findByWorkspaceIdAndNameIgnoreCase(workspaceId, name).ifPresent(existing -> {
            if (!existing.getId().equals(channelId)) {
                throw new RuntimeException("Kênh \"" + name + "\" đã tồn tại");
            }
        });
        channel.setName(name);
        WorkspaceChannelResponse response = mapChannel(channelRepository.save(channel));
        workspaceRealtimeService.publishChannelChanged(workspaceId, "CHANNEL_UPDATED", channelId);
        return response;
    }

    @Override
    @Transactional
    public void deleteChannel(String email, Long workspaceId, Long channelId) {
        User user = requireUser(email);
        assertIsMember(user, workspaceId);
        WorkspaceChannel channel = getChannelOrThrow(channelId, workspaceId);
        if (Boolean.TRUE.equals(channel.getIsSystem())) {
            throw new RuntimeException("Không thể xóa kênh hệ thống");
        }
        channelRepository.delete(channel);
        workspaceRealtimeService.publishChannelChanged(workspaceId, "CHANNEL_DELETED", channelId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceChatMessageResponse> listChannelMessages(String email, Long workspaceId, Long channelId) {
        User user = requireUser(email);
        assertIsMember(user, workspaceId);
        getChannelOrThrow(channelId, workspaceId);
        return channelMessageRepository.findTopLevelByChannelId(channelId).stream()
                .map(this::mapChannelMessage)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceChatMessageResponse> listChannelThreadReplies(String email, Long workspaceId, Long channelId) {
        User user = requireUser(email);
        assertIsMember(user, workspaceId);
        getChannelOrThrow(channelId, workspaceId);
        return channelMessageRepository.findThreadRepliesByChannelId(channelId).stream()
                .map(this::mapChannelMessage)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public WorkspaceChatMessageResponse sendChannelMessage(String email, Long workspaceId, Long channelId,
            WorkspaceChatMessageRequest request) {
        User user = requireUser(email);
        assertIsMember(user, workspaceId);
        WorkspaceChannel channel = getChannelOrThrow(channelId, workspaceId);

        ChannelMessageKind kind = request.getKind() != null ? request.getKind() : ChannelMessageKind.USER;
        String content = request.getContent() != null ? request.getContent().trim() : "";
        boolean hasImage = request.getImageUrl() != null && !request.getImageUrl().isBlank();
        boolean hasTool = request.getToolResultJson() != null && !request.getToolResultJson().isBlank();

        if (content.isEmpty() && !hasImage && !hasTool) {
            throw new RuntimeException("Nội dung tin nhắn trống");
        }

        ChannelMessage parent = null;
        if (request.getParentMessageId() != null) {
            parent = channelMessageRepository.findById(request.getParentMessageId())
                    .orElseThrow(() -> new RuntimeException("Tin nhắn gốc không tồn tại"));
            if (!parent.getChannel().getId().equals(channelId)) {
                throw new RuntimeException("Tin nhắn gốc không thuộc kênh này");
            }
            if (parent.getParentMessage() != null) {
                throw new RuntimeException("Chỉ trả lời được tin nhắn gốc trên kênh");
            }
        } else if (content.isEmpty() && !hasImage) {
            throw new RuntimeException("Nội dung tin nhắn trống");
        }

        if (kind == ChannelMessageKind.WILLA && parent == null) {
            throw new RuntimeException("Phản hồi WillA phải nằm trong thread");
        }

        ChannelMessage message = channelMessageRepository.save(ChannelMessage.builder()
                .channel(channel)
                .user(kind == ChannelMessageKind.WILLA ? null : user)
                .parentMessage(parent)
                .messageKind(kind)
                .content(content.isEmpty() ? (hasTool ? " " : "📎") : content)
                .imageUrl(request.getImageUrl())
                .toolResultJson(request.getToolResultJson())
                .build());
        WorkspaceChatMessageResponse response = mapChannelMessage(message);
        if (parent != null) {
            workspaceRealtimeService.publishChannelThreadReply(workspaceId, channelId, parent.getId(), response);
        } else {
            workspaceRealtimeService.publishChannelMessage(workspaceId, channelId, response);
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceChatMessageResponse> listDmMessages(String email, Long workspaceId, Long peerUserId) {
        User user = requireUser(email);
        assertIsMember(user, workspaceId);
        assertPeerIsMember(workspaceId, peerUserId);
        if (user.getId().equals(peerUserId)) {
            throw new RuntimeException("Không thể chat với chính mình");
        }
        WorkspaceDmConversation conversation = findOrCreateConversation(workspaceId, user.getId(), peerUserId);
        return dmMessageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(conversation.getId()).stream()
                .map(this::mapDmMessage)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public WorkspaceChatMessageResponse sendDmMessage(String email, Long workspaceId, Long peerUserId,
            WorkspaceChatMessageRequest request) {
        User user = requireUser(email);
        assertIsMember(user, workspaceId);
        assertPeerIsMember(workspaceId, peerUserId);
        if (user.getId().equals(peerUserId)) {
            throw new RuntimeException("Không thể chat với chính mình");
        }
        String content = request.getContent() != null ? request.getContent().trim() : "";
        if (content.isEmpty()) {
            throw new RuntimeException("Nội dung tin nhắn trống");
        }
        WorkspaceDmConversation conversation = findOrCreateConversation(workspaceId, user.getId(), peerUserId);
        WorkspaceDmMessage message = dmMessageRepository.save(WorkspaceDmMessage.builder()
                .conversation(conversation)
                .sender(user)
                .content(content)
                .build());
        WorkspaceChatMessageResponse response = mapDmMessage(message);
        workspaceRealtimeService.publishDmMessage(workspaceId, conversation.getId(), peerUserId, response);
        return response;
    }

    private WorkspaceDmConversation findOrCreateConversation(Long workspaceId, Long userId1, Long userId2) {
        return dmConversationRepository.findByWorkspaceAndUsers(workspaceId, userId1, userId2)
                .orElseGet(() -> {
                    Workspace workspace = getWorkspaceOrThrow(workspaceId);
                    long a = Math.min(userId1, userId2);
                    long b = Math.max(userId1, userId2);
                    User userA = userRepository.findById(a).orElseThrow(() -> new RuntimeException("User not found"));
                    User userB = userRepository.findById(b).orElseThrow(() -> new RuntimeException("User not found"));
                    return dmConversationRepository.save(WorkspaceDmConversation.builder()
                            .workspace(workspace)
                            .userA(userA)
                            .userB(userB)
                            .build());
                });
    }

    private String normalizeChannelName(String raw) {
        String name = raw.trim();
        if (name.startsWith("#")) {
            name = name.substring(1).trim();
        }
        if (name.isEmpty()) {
            throw new RuntimeException("Tên kênh không hợp lệ");
        }
        if (name.length() > 100) {
            throw new RuntimeException("Tên kênh quá dài (tối đa 100 ký tự)");
        }
        return name;
    }

    private WorkspaceChannel getChannelOrThrow(Long channelId, Long workspaceId) {
        return channelRepository.findByIdAndWorkspaceId(channelId, workspaceId)
                .orElseThrow(() -> new RuntimeException("Kênh không tồn tại"));
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
    }

    private Workspace getWorkspaceOrThrow(Long workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new RuntimeException("Workspace not found"));
    }

    private void assertIsMember(User user, Long workspaceId) {
        Workspace workspace = getWorkspaceOrThrow(workspaceId);
        if (workspace.getOwner().getId().equals(user.getId())) {
            return;
        }
        workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, user.getId())
                .orElseThrow(() -> new RuntimeException("You are not a member of this workspace"));
    }

    private void assertPeerIsMember(Long workspaceId, Long peerUserId) {
        Workspace workspace = getWorkspaceOrThrow(workspaceId);
        if (workspace.getOwner().getId().equals(peerUserId)) {
            return;
        }
        workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, peerUserId)
                .orElseThrow(() -> new RuntimeException("Thành viên không thuộc workspace này"));
    }

    private WorkspaceChannelResponse mapChannel(WorkspaceChannel channel) {
        return WorkspaceChannelResponse.builder()
                .id(channel.getId())
                .workspaceId(channel.getWorkspace().getId())
                .name(channel.getName())
                .position(channel.getPosition())
                .isSystem(channel.getIsSystem())
                .messageCount(channelMessageRepository.countByChannelId(channel.getId()))
                .createdAt(channel.getCreatedAt())
                .build();
    }

    private WorkspaceChatMessageResponse mapChannelMessage(ChannelMessage message) {
        User author = message.getUser();
        ChannelMessageKind kind = message.getMessageKind() != null
                ? message.getMessageKind()
                : ChannelMessageKind.USER;
        Long parentId = message.getParentMessage() != null ? message.getParentMessage().getId() : null;
        return WorkspaceChatMessageResponse.builder()
                .id(message.getId())
                .channelId(message.getChannel().getId())
                .parentMessageId(parentId)
                .kind(kind)
                .userId(author != null ? author.getId() : null)
                .userName(kind == ChannelMessageKind.WILLA
                        ? "WillA"
                        : author != null ? displayName(author) : null)
                .userAvatarUrl(author != null ? author.getAvatarUrl() : null)
                .content(message.getContent())
                .imageUrl(message.getImageUrl())
                .toolResultJson(message.getToolResultJson())
                .createdAt(message.getCreatedAt())
                .build();
    }

    private WorkspaceChatMessageResponse mapDmMessage(WorkspaceDmMessage message) {
        User sender = message.getSender();
        return WorkspaceChatMessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversation().getId())
                .userId(sender.getId())
                .userName(displayName(sender))
                .userAvatarUrl(sender.getAvatarUrl())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build();
    }

    private String displayName(User user) {
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName();
        }
        return user.getEmail();
    }
}
