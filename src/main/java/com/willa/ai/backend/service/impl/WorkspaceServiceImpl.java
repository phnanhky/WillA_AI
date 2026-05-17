package com.willa.ai.backend.service.impl;

import com.willa.ai.backend.dto.request.WorkspaceRequest;
import com.willa.ai.backend.dto.request.InviteMemberRequest;
import com.willa.ai.backend.dto.request.AcceptInviteRequest;
import com.willa.ai.backend.dto.request.UpdateMemberRoleRequest;
import com.willa.ai.backend.dto.response.WorkspaceResponse;
import com.willa.ai.backend.dto.response.WorkspaceMemberResponse;
import com.willa.ai.backend.dto.response.WorkspaceShareResponse;
import com.willa.ai.backend.dto.response.WorkspaceInviteResponse;
import com.willa.ai.backend.dto.response.InviteMemberResultResponse;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.Workspace;
import com.willa.ai.backend.entity.WorkspaceMember;
import com.willa.ai.backend.entity.WorkspaceInvite;
import com.willa.ai.backend.entity.enums.WorkspaceRole;
import com.willa.ai.backend.entity.enums.InviteStatus;
import com.willa.ai.backend.repository.WorkspaceInviteRepository;
import com.willa.ai.backend.service.EmailService;
import com.willa.ai.backend.entity.Subscription;
import com.willa.ai.backend.entity.enums.SubscriptionStatus;
import com.willa.ai.backend.repository.SubscriptionRepository;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.repository.WorkspaceMemberRepository;
import com.willa.ai.backend.repository.WorkspaceRepository;
import com.willa.ai.backend.service.WorkspaceService;

import com.willa.ai.backend.dto.request.AddWorkspacePageRequest;
import com.willa.ai.backend.dto.request.PageCommentRequest;
import com.willa.ai.backend.dto.response.PageCommentResponse;
import com.willa.ai.backend.dto.response.WorkspacePageResponse;
import com.willa.ai.backend.entity.PageComment;
import com.willa.ai.backend.repository.PageCommentRepository;
import com.willa.ai.backend.repository.ChatSessionRepository;
import com.willa.ai.backend.entity.ChatSession;
import com.willa.ai.backend.entity.WorkspacePage;
import com.willa.ai.backend.repository.WorkspacePageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkspaceServiceImpl implements WorkspaceService {
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final WorkspacePageRepository workspacePageRepository;
    private final PageCommentRepository pageCommentRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final WorkspaceInviteRepository workspaceInviteRepository;
    private final EmailService emailService;

    @Value("${app.frontendUrl:http://localhost:5173}")
    private String frontendUrl;

    @Override
    @Transactional
    public WorkspaceResponse createWorkspace(String email, WorkspaceRequest request) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        
        Subscription activeSub = subscriptionRepository.findByUserIdAndStatus(user.getId(), SubscriptionStatus.ACTIVE).stream().findFirst().orElse(null);
        String planName = (activeSub != null && activeSub.getPlan() != null) ? activeSub.getPlan().getName() : "Free";
        
        int currentWorkspaceCount = workspaceRepository.countByOwnerId(user.getId());

        if (planName.equalsIgnoreCase("Free") && currentWorkspaceCount >= 1) {
            throw new RuntimeException("Tài khoản Free chỉ có tối đa 1 Personal Workspace. Hãy nâng cấp!");
        } else if (planName.equalsIgnoreCase("Student") && currentWorkspaceCount >= 3) {
            throw new RuntimeException("Sinh viên được tạo tối đa 3 Nhóm/Workspace!");
        }

        Workspace w = workspaceRepository.save(Workspace.builder().name(request.getName()).description(request.getDescription()).owner(user).build());
        workspaceMemberRepository.save(WorkspaceMember.builder().workspace(w).user(user).role(WorkspaceRole.ADMIN).build());
        return mapToResponse(w);
    }

    @Override
    @Transactional
    public WorkspaceResponse updateWorkspace(String email, Long workspaceId, WorkspaceRequest request) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new RuntimeException("Workspace not found"));

        boolean isOwner = workspace.getOwner().getId().equals(user.getId());
        boolean isAdmin = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, user.getId())
                .map(m -> m.getRole() == WorkspaceRole.ADMIN)
                .orElse(false);

        if (!isOwner && !isAdmin) {
            throw new RuntimeException("Bạn không có quyền chỉnh sửa workspace này");
        }

        workspace.setName(request.getName().trim());
        if (request.getDescription() != null) {
            workspace.setDescription(request.getDescription());
        }
        Workspace saved = workspaceRepository.save(workspace);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public void deleteWorkspace(String email, Long workspaceId) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new RuntimeException("Workspace not found"));

        if (!workspace.getOwner().getId().equals(user.getId())) {
            throw new RuntimeException("Chỉ chủ sở hữu mới có thể xóa workspace này");
        }

        for (ChatSession session : chatSessionRepository.findByWorkspaceId(workspaceId)) {
            session.setWorkspace(null);
            chatSessionRepository.save(session);
        }

        List<WorkspacePage> pages = workspacePageRepository.findByWorkspaceIdOrderByPageNumberAsc(workspaceId);
        for (WorkspacePage page : pages) {
            pageCommentRepository.deleteByWorkspacePageId(page.getId());
        }
        workspacePageRepository.deleteByWorkspaceId(workspaceId);
        workspaceMemberRepository.deleteByWorkspaceId(workspaceId);
        workspaceInviteRepository.deleteByWorkspaceId(workspaceId);
        workspaceRepository.delete(workspace);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceResponse> getUserWorkspaces(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        Set<Long> seen = new LinkedHashSet<>();
        List<WorkspaceResponse> result = new ArrayList<>();
        for (Workspace w : workspaceRepository.findByOwnerId(user.getId())) {
            if (seen.add(w.getId())) {
                result.add(mapToResponse(w));
            }
        }
        for (WorkspaceMember m : workspaceMemberRepository.findByUserId(user.getId())) {
            Long wid = m.getWorkspace().getId();
            if (seen.add(wid)) {
                result.add(mapToResponse(m.getWorkspace()));
            }
        }
        return result;
    }

    @Override
    @Transactional
    public InviteMemberResultResponse inviteMember(String currentEmail, Long workspaceId, InviteMemberRequest request) {
        User currentUser = userRepository.findByEmail(currentEmail).orElseThrow(() -> new RuntimeException("User not found"));
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow(() -> new RuntimeException("Workspace not found"));
        assertCanManageMembers(currentUser, workspace);

        String inviteEmail = request.getEmail().trim().toLowerCase();
        if (inviteEmail.equals(currentUser.getEmail().toLowerCase())) {
            throw new RuntimeException("Bạn không thể mời chính mình");
        }
        if (inviteEmail.equals(workspace.getOwner().getEmail().toLowerCase())) {
            throw new RuntimeException("Chủ sở hữu đã có trong workspace");
        }

        assertMemberLimitNotExceeded(workspace);

        WorkspaceRole role = request.getRole() != null ? request.getRole() : WorkspaceRole.VIEWER;
        if (role == WorkspaceRole.ADMIN && !workspace.getOwner().getId().equals(currentUser.getId())) {
            throw new RuntimeException("Chỉ chủ sở hữu mới có thể mời quyền ADMIN");
        }

        var existingUser = userRepository.findByEmail(inviteEmail);
        if (existingUser.isPresent()) {
            User invitee = existingUser.get();
            if (workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, invitee.getId()).isPresent()) {
                throw new RuntimeException("Người dùng đã là thành viên của workspace");
            }
            WorkspaceMember newMember = workspaceMemberRepository.save(WorkspaceMember.builder()
                    .workspace(workspace)
                    .user(invitee)
                    .role(role)
                    .build());
            return InviteMemberResultResponse.builder()
                    .resultType("MEMBER_ADDED")
                    .message("Đã thêm thành viên vào workspace")
                    .member(mapToMemberResponse(newMember))
                    .build();
        }

        workspaceInviteRepository.findByWorkspaceIdAndEmailAndStatus(workspaceId, inviteEmail, InviteStatus.PENDING)
                .ifPresent(inv -> {
                    throw new RuntimeException("Đã có lời mời đang chờ cho email này");
                });

        String token = UUID.randomUUID().toString().replace("-", "");
        WorkspaceInvite invite = workspaceInviteRepository.save(WorkspaceInvite.builder()
                .workspace(workspace)
                .email(inviteEmail)
                .role(role)
                .token(token)
                .status(InviteStatus.PENDING)
                .invitedBy(currentUser)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build());

        String inviteLink = buildInviteLink(token);
        emailService.sendWorkspaceInviteEmail(
                inviteEmail,
                workspace.getName(),
                currentUser.getFullName(),
                inviteLink,
                role.name());

        return InviteMemberResultResponse.builder()
                .resultType("INVITE_SENT")
                .message("Đã gửi lời mời qua email. Người nhận cần đăng ký tài khoản rồi chấp nhận lời mời.")
                .invite(mapToInviteResponse(invite))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceShareResponse getWorkspaceShare(String email, Long workspaceId) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow(() -> new RuntimeException("Workspace not found"));
        assertIsMember(user, workspaceId);

        boolean canManage = canManageMembers(user, workspace);
        List<WorkspaceMemberResponse> members = workspaceMemberRepository.findByWorkspaceId(workspaceId).stream()
                .map(this::mapToMemberResponse)
                .collect(Collectors.toList());
        List<WorkspaceInviteResponse> pending = canManage
                ? workspaceInviteRepository.findByWorkspaceIdAndStatus(workspaceId, InviteStatus.PENDING).stream()
                        .map(this::mapToInviteResponse)
                        .collect(Collectors.toList())
                : List.of();

        return WorkspaceShareResponse.builder()
                .workspaceId(workspace.getId())
                .workspaceName(workspace.getName())
                .ownerId(workspace.getOwner().getId())
                .ownerName(workspace.getOwner().getFullName())
                .members(members)
                .pendingInvites(pending)
                .canManageMembers(canManage)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceInviteResponse> getPendingInvites(String email, Long workspaceId) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow(() -> new RuntimeException("Workspace not found"));
        assertCanManageMembers(user, workspace);
        return workspaceInviteRepository.findByWorkspaceIdAndStatus(workspaceId, InviteStatus.PENDING).stream()
                .map(this::mapToInviteResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void revokeInvite(String email, Long workspaceId, Long inviteId) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow(() -> new RuntimeException("Workspace not found"));
        assertCanManageMembers(user, workspace);

        WorkspaceInvite invite = workspaceInviteRepository.findById(inviteId)
                .orElseThrow(() -> new RuntimeException("Invite not found"));
        if (!invite.getWorkspace().getId().equals(workspaceId)) {
            throw new RuntimeException("Invite does not belong to this workspace");
        }
        invite.setStatus(InviteStatus.REVOKED);
        workspaceInviteRepository.save(invite);
    }

    @Override
    @Transactional
    public WorkspaceMemberResponse acceptInvite(String email, AcceptInviteRequest request) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        WorkspaceInvite invite = workspaceInviteRepository.findByTokenAndStatus(request.getToken().trim(), InviteStatus.PENDING)
                .orElseThrow(() -> new RuntimeException("Lời mời không hợp lệ hoặc đã hết hạn"));

        if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            invite.setStatus(InviteStatus.EXPIRED);
            workspaceInviteRepository.save(invite);
            throw new RuntimeException("Lời mời đã hết hạn");
        }

        if (!invite.getEmail().equalsIgnoreCase(user.getEmail())) {
            throw new RuntimeException("Lời mời không dành cho tài khoản này");
        }

        Workspace workspace = invite.getWorkspace();
        if (workspaceMemberRepository.findByWorkspaceIdAndUserId(workspace.getId(), user.getId()).isPresent()) {
            invite.setStatus(InviteStatus.ACCEPTED);
            workspaceInviteRepository.save(invite);
            throw new RuntimeException("Bạn đã là thành viên của workspace này");
        }

        assertMemberLimitNotExceeded(workspace);

        WorkspaceMember member = workspaceMemberRepository.save(WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(invite.getRole())
                .build());

        invite.setStatus(InviteStatus.ACCEPTED);
        workspaceInviteRepository.save(invite);

        return mapToMemberResponse(member);
    }

    @Override
    @Transactional
    public void removeMember(String email, Long workspaceId, Long memberId) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow(() -> new RuntimeException("Workspace not found"));
        assertCanManageMembers(user, workspace);

        WorkspaceMember member = workspaceMemberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));
        if (!member.getWorkspace().getId().equals(workspaceId)) {
            throw new RuntimeException("Member does not belong to this workspace");
        }
        if (member.getUser().getId().equals(workspace.getOwner().getId())) {
            throw new RuntimeException("Không thể xóa chủ sở hữu workspace");
        }
        if (member.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Bạn không thể tự xóa chính mình. Hãy rời workspace bằng cách khác.");
        }
        workspaceMemberRepository.delete(member);
    }

    @Override
    @Transactional
    public WorkspaceMemberResponse updateMemberRole(String email, Long workspaceId, Long memberId, UpdateMemberRoleRequest request) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow(() -> new RuntimeException("Workspace not found"));
        assertCanManageMembers(user, workspace);

        WorkspaceMember member = workspaceMemberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));
        if (!member.getWorkspace().getId().equals(workspaceId)) {
            throw new RuntimeException("Member does not belong to this workspace");
        }
        if (member.getUser().getId().equals(workspace.getOwner().getId())) {
            throw new RuntimeException("Không thể đổi quyền chủ sở hữu");
        }
        if (request.getRole() == WorkspaceRole.ADMIN && !workspace.getOwner().getId().equals(user.getId())) {
            throw new RuntimeException("Chỉ chủ sở hữu mới gán quyền ADMIN");
        }

        member.setRole(request.getRole());
        return mapToMemberResponse(workspaceMemberRepository.save(member));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceMemberResponse> getWorkspaceMembers(String email, Long workspaceId) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        
        if (workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, user.getId()).isEmpty()) {
            throw new RuntimeException("You do not have access to this workspace");
        }
        
        return workspaceMemberRepository.findByWorkspaceId(workspaceId).stream()
            .map(this::mapToMemberResponse)
            .collect(Collectors.toList());
    }

    private static final String BLANK_PAGE_DATA_URI_PREFIX =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAAB";

    private String resolveThumbnailUrl(Long workspaceId) {
        return workspacePageRepository.findByWorkspaceIdOrderByPageNumberAsc(workspaceId).stream()
                .map(WorkspacePage::getImageUrl)
                .filter(url -> url != null && !url.isBlank() && !isBlankPlaceholder(url))
                .findFirst()
                .orElse(null);
    }

    private boolean isBlankPlaceholder(String imageUrl) {
        return imageUrl.startsWith(BLANK_PAGE_DATA_URI_PREFIX);
    }

    private WorkspaceResponse mapToResponse(Workspace workspace) {
        Subscription activeSub = subscriptionRepository.findByUserIdAndStatus(workspace.getOwner().getId(), SubscriptionStatus.ACTIVE).stream().findFirst().orElse(null);
        String planName = (activeSub != null && activeSub.getPlan() != null) ? activeSub.getPlan().getName() : "Free";
        long maxStorage;
        if (planName.equalsIgnoreCase("Free")) {
            maxStorage = 500L * 1024 * 1024;
        } else if (planName.equalsIgnoreCase("Student")) {
            maxStorage = 2L * 1024 * 1024 * 1024;
        } else {
            maxStorage = 100L * 1024 * 1024 * 1024;
        }
        return WorkspaceResponse.builder()
            .id(workspace.getId())
            .name(workspace.getName())
            .description(workspace.getDescription())
            .ownerId(workspace.getOwner().getId())
            .ownerName(workspace.getOwner().getFullName())
            .createdAt(workspace.getCreatedAt())
            .updatedAt(workspace.getUpdatedAt())
            .thumbnailUrl(resolveThumbnailUrl(workspace.getId()))
            .storageUsed(workspace.getStorageUsed() != null ? workspace.getStorageUsed() : 0L)
            .maxStorageLimits(maxStorage)
            .isPublic(workspace.getIsPublic())
            .likesCount(workspace.getLikesCount())
            .clonesCount(workspace.getClonesCount())
            .build();
    }
    
    private WorkspaceMemberResponse mapToMemberResponse(WorkspaceMember member) {
        return WorkspaceMemberResponse.builder()
            .id(member.getId())
            .workspaceId(member.getWorkspace().getId())
            .userId(member.getUser().getId())
            .userName(member.getUser().getFullName())
            .email(member.getUser().getEmail())
            .role(member.getRole())
            .joinedAt(member.getJoinedAt())
            .build();
    }

    @Override
    @Transactional
    public WorkspacePageResponse addPageToWorkspace(String email, Long workspaceId, AddWorkspacePageRequest request) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow(() -> new RuntimeException("Workspace not found"));

        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, user.getId())
                .orElseThrow(() -> new RuntimeException("You are not a member of this workspace"));

        if (member.getRole() == WorkspaceRole.VIEWER) {
            throw new RuntimeException("Viewers cannot modify workspace pages");
        }

        User owner = workspace.getOwner();
        Subscription activeSub = subscriptionRepository.findByUserIdAndStatus(owner.getId(), SubscriptionStatus.ACTIVE).stream().findFirst().orElse(null);
        String planName = (activeSub != null && activeSub.getPlan() != null) ? activeSub.getPlan().getName() : "Free";

        long currentStorage = workspace.getStorageUsed() != null ? workspace.getStorageUsed() : 0L;
        long newFileSize = request.getFileSizeBytes();
        long maxStorage;

        if (planName.equalsIgnoreCase("Free")) {
            maxStorage = 500L * 1024 * 1024;
        } else if (planName.equalsIgnoreCase("Student")) {
            maxStorage = 2L * 1024 * 1024 * 1024;
        } else {
            maxStorage = 100L * 1024 * 1024 * 1024;
        }

        if (currentStorage + newFileSize > maxStorage) {
            throw new RuntimeException("Đã vượt quá dung lượng cho phép của Workspace (" + (maxStorage / (1024 * 1024)) + " MB)");
        }

        workspace.setStorageUsed(currentStorage + newFileSize);
        workspaceRepository.save(workspace);

        WorkspacePage page = workspacePageRepository.findByWorkspaceIdAndPageNumber(workspaceId, request.getPageNumber()).orElse(null);
        if (page != null) {
            workspace.setStorageUsed(workspace.getStorageUsed() - page.getFileSizeBytes());
            page.setImageUrl(request.getImageUrl());
            page.setFileSizeBytes(request.getFileSizeBytes());
            workspacePageRepository.save(page);
        } else {
            page = workspacePageRepository.save(WorkspacePage.builder()
                    .workspace(workspace)
                    .pageNumber(request.getPageNumber())
                    .imageUrl(request.getImageUrl())
                    .fileSizeBytes(request.getFileSizeBytes())
                    .build());
        }
        return mapToPageResponse(page);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspacePageResponse> getWorkspacePages(String email, Long workspaceId) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        if (workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, user.getId()).isEmpty()) {
            throw new RuntimeException("You do not have access to this workspace");
        }
        return workspacePageRepository.findByWorkspaceIdOrderByPageNumberAsc(workspaceId).stream()
                .map(this::mapToPageResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public WorkspacePageResponse updatePageDesign(String email, Long workspaceId, Long pageId, String designData) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new RuntimeException("Workspace not found"));

        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, user.getId())
                .orElseThrow(() -> new RuntimeException("You are not a member of this workspace"));

        if (member.getRole() == WorkspaceRole.VIEWER) {
            throw new RuntimeException("Viewers cannot modify workspace pages");
        }

        WorkspacePage page = workspacePageRepository.findById(pageId)
                .orElseThrow(() -> new RuntimeException("Page not found"));
        if (!page.getWorkspace().getId().equals(workspaceId)) {
            throw new RuntimeException("Page does not belong to this workspace");
        }

        page.setDesignData(designData);
        return mapToPageResponse(workspacePageRepository.save(page));
    }

    @Override
    @Transactional
    public void deleteWorkspacePage(String email, Long workspaceId, Long pageId) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow(() -> new RuntimeException("Workspace not found"));
        
        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, user.getId())
            .orElseThrow(() -> new RuntimeException("You are not a member of this workspace"));
            
        if (member.getRole() == WorkspaceRole.VIEWER) {
            throw new RuntimeException("Viewers cannot delete workspace pages");
        }
        
        WorkspacePage page = workspacePageRepository.findById(pageId).orElseThrow(() -> new RuntimeException("Page not found"));
        if (!page.getWorkspace().getId().equals(workspaceId)) {
            throw new RuntimeException("Page does not belong to this workspace");
        }
        
        // Subtract storage
        long currentStorage = workspace.getStorageUsed() != null ? workspace.getStorageUsed() : 0L;
        long pageSize = page.getFileSizeBytes() != null ? page.getFileSizeBytes() : 0L;
        workspace.setStorageUsed(Math.max(0, currentStorage - pageSize));
        workspaceRepository.save(workspace);
        
        workspacePageRepository.delete(page);
    }

    @Override
    @Transactional
    public void reorderPages(String email, Long workspaceId, List<Long> pageIds) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, user.getId())
            .orElseThrow(() -> new RuntimeException("You do not have access to this workspace"));
            
        if (member.getRole() == WorkspaceRole.VIEWER) {
            throw new RuntimeException("Viewers cannot reorder pages");
        }
        
        for (int i = 0; i < pageIds.size(); i++) {
            Long pageId = pageIds.get(i);
            WorkspacePage page = workspacePageRepository.findById(pageId).orElse(null);
            if (page != null && page.getWorkspace().getId().equals(workspaceId)) {
                page.setPageNumber(i + 1);
                workspacePageRepository.save(page);
            }
        }
    }

    @Override
    @Transactional
    public PageCommentResponse addComment(String email, Long pageId, PageCommentRequest request) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        WorkspacePage page = workspacePageRepository.findById(pageId).orElseThrow(() -> new RuntimeException("Page not found"));

        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(page.getWorkspace().getId(), user.getId())
            .orElseThrow(() -> new RuntimeException("Not a member"));

        if (member.getRole() == WorkspaceRole.VIEWER) {
            throw new RuntimeException("Viewers cannot add comments");
        }

        PageComment comment = pageCommentRepository.save(PageComment.builder()
            .workspacePage(page)
            .user(user)
            .content(request.getContent())
            .coordinateX(request.getCoordinateX())
            .coordinateY(request.getCoordinateY())
            .resolved(false)
            .build());

        return mapToCommentResponse(comment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PageCommentResponse> getComments(String email, Long pageId) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        WorkspacePage page = workspacePageRepository.findById(pageId).orElseThrow(() -> new RuntimeException("Page not found"));

        if (workspaceMemberRepository.findByWorkspaceIdAndUserId(page.getWorkspace().getId(), user.getId()).isEmpty()) {
            throw new RuntimeException("Not a member");
        }

        return pageCommentRepository.findByWorkspacePageIdOrderByCreatedAtAsc(pageId).stream()
            .map(this::mapToCommentResponse)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteComment(String email, Long commentId) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        PageComment comment = pageCommentRepository.findById(commentId).orElseThrow(() -> new RuntimeException("Comment not found"));

        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(comment.getWorkspacePage().getWorkspace().getId(), user.getId())
            .orElseThrow(() -> new RuntimeException("Not a member"));

        if (member.getRole() != WorkspaceRole.ADMIN && !comment.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("You don't have permission to delete this comment");
        }

        pageCommentRepository.delete(comment);
    }

    private PageCommentResponse mapToCommentResponse(PageComment comment) {
        return PageCommentResponse.builder()
            .id(comment.getId())
            .pageId(comment.getWorkspacePage().getId())
            .userId(comment.getUser().getId())
            .userName(comment.getUser().getFullName())
            .content(comment.getContent())
            .coordinateX(comment.getCoordinateX())
            .coordinateY(comment.getCoordinateY())
            .resolved(comment.getResolved())
            .createdAt(comment.getCreatedAt())
            .build();
    }

    private WorkspacePageResponse mapToPageResponse(WorkspacePage page) {
        return WorkspacePageResponse.builder()
                .id(page.getId())
                .workspaceId(page.getWorkspace().getId())
                .pageNumber(page.getPageNumber())
                .imageUrl(page.getImageUrl())
                .fileSizeBytes(page.getFileSizeBytes())
                .designData(page.getDesignData())
                .build();
    }

    @Override
    @Transactional
    public WorkspaceResponse shareToCommunity(String email, Long workspaceId) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow(() -> new RuntimeException("Workspace not found"));
        
        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, user.getId())
            .orElseThrow(() -> new RuntimeException("Not a member"));
            
        if (member.getRole() != WorkspaceRole.ADMIN) {
            if (!workspace.getOwner().getId().equals(user.getId())) {
                throw new RuntimeException("Only ADMINs or Owner can share to community");
            }
        }
        
        workspace.setIsPublic(true);
        workspace = workspaceRepository.save(workspace);
        
        return mapToResponse(workspace);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WorkspaceResponse> exploreWorkspaces(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Workspace> publicWorkspaces = workspaceRepository.findByIsPublicTrue(pageable);
        return publicWorkspaces.map(this::mapToResponse);
    }

    private void assertIsMember(User user, Long workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new RuntimeException("Workspace not found"));
        if (workspace.getOwner().getId().equals(user.getId())) {
            return;
        }
        if (workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, user.getId()).isEmpty()) {
            throw new RuntimeException("You do not have access to this workspace");
        }
    }

    private boolean canManageMembers(User user, Workspace workspace) {
        if (workspace.getOwner().getId().equals(user.getId())) {
            return true;
        }
        return workspaceMemberRepository.findByWorkspaceIdAndUserId(workspace.getId(), user.getId())
                .map(m -> m.getRole() == WorkspaceRole.ADMIN)
                .orElse(false);
    }

    private void assertCanManageMembers(User user, Workspace workspace) {
        if (!canManageMembers(user, workspace)) {
            throw new RuntimeException("Chỉ chủ sở hữu hoặc ADMIN mới quản lý được thành viên");
        }
    }

    private void assertMemberLimitNotExceeded(Workspace workspace) {
        User owner = workspace.getOwner();
        Subscription activeSub = subscriptionRepository.findByUserIdAndStatus(owner.getId(), SubscriptionStatus.ACTIVE)
                .stream().findFirst().orElse(null);
        String planName = (activeSub != null && activeSub.getPlan() != null) ? activeSub.getPlan().getName() : "Free";
        long memberCount = workspaceMemberRepository.countByWorkspaceId(workspace.getId());
        if (planName.equalsIgnoreCase("Free") && memberCount >= 2) {
            throw new RuntimeException("Tài khoản Free chỉ cho phép tối đa 2 người trong 1 Workspace!");
        } else if (planName.equalsIgnoreCase("Student") && memberCount >= 5) {
            throw new RuntimeException("Sinh viên chỉ cho phép tối đa 5 người trong 1 Workspace!");
        }
    }

    private String buildInviteLink(String token) {
        return frontendUrl + "/projects?inviteToken=" + token;
    }

    private WorkspaceInviteResponse mapToInviteResponse(WorkspaceInvite invite) {
        return WorkspaceInviteResponse.builder()
                .id(invite.getId())
                .workspaceId(invite.getWorkspace().getId())
                .workspaceName(invite.getWorkspace().getName())
                .email(invite.getEmail())
                .role(invite.getRole())
                .status(invite.getStatus())
                .invitedByName(invite.getInvitedBy().getFullName())
                .inviteLink(buildInviteLink(invite.getToken()))
                .expiresAt(invite.getExpiresAt())
                .createdAt(invite.getCreatedAt())
                .build();
    }
}
