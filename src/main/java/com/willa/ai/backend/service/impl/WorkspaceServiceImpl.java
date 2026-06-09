package com.willa.ai.backend.service.impl;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.willa.ai.backend.dto.request.AcceptInviteRequest;
import com.willa.ai.backend.dto.request.InviteMemberRequest;
import com.willa.ai.backend.dto.request.JoinWorkspaceByCodeRequest;
import com.willa.ai.backend.dto.request.UpdateMemberImportantRequest;
import com.willa.ai.backend.dto.request.UpdateMemberRoleRequest;
import com.willa.ai.backend.dto.request.WorkspaceRequest;
import com.willa.ai.backend.dto.response.InviteMemberResultResponse;
import com.willa.ai.backend.dto.response.WorkspaceInviteResponse;
import com.willa.ai.backend.dto.response.WorkspaceMemberResponse;
import com.willa.ai.backend.dto.response.WorkspaceResponse;
import com.willa.ai.backend.dto.response.WorkspaceShareResponse;
import com.willa.ai.backend.entity.Subscription;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.Workspace;
import com.willa.ai.backend.entity.WorkspaceInvite;
import com.willa.ai.backend.entity.WorkspaceMember;
import com.willa.ai.backend.entity.enums.InviteStatus;
import com.willa.ai.backend.entity.enums.SubscriptionStatus;
import com.willa.ai.backend.entity.enums.WorkspaceRole;
import com.willa.ai.backend.repository.SubscriptionRepository;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.repository.WorkspaceInviteRepository;
import com.willa.ai.backend.repository.WorkspaceMemberRepository;
import com.willa.ai.backend.repository.WorkspaceRepository;
import com.willa.ai.backend.service.EmailService;
import com.willa.ai.backend.service.FileService;
import com.willa.ai.backend.service.WorkspaceService;
import com.willa.ai.backend.util.QrCodeGenerator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkspaceServiceImpl implements WorkspaceService {

    private static final String INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceInviteRepository workspaceInviteRepository;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final EmailService emailService;
    private final FileService fileService;

    @Value("${app.frontendUrl:http://localhost:5173}")
    private String frontendUrl;

    @Value("${cloudflare.r2.qr-prefix:qr code/}")
    private String qrObjectPrefix;

    @Override
    @Transactional
    public WorkspaceResponse createWorkspace(String email, WorkspaceRequest request) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        assertWorkspaceCreateAllowed(user);

        String inviteCode = generateUniqueInviteCode();
        String inviteLink = buildJoinLink(inviteCode);
        String qrCodeUrl = uploadInviteQrCode(inviteLink, inviteCode);

        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .owner(user)
                .inviteCode(inviteCode)
                .qrCodeUrl(qrCodeUrl)
                .build());

        workspaceMemberRepository.save(WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(WorkspaceRole.OWNER)
                .isImportant(false)
                .build());

        return mapToResponse(workspace, user);
    }

    @Override
    @Transactional
    public WorkspaceResponse updateWorkspace(String email, Long workspaceId, WorkspaceRequest request) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        Workspace workspace = getWorkspaceOrThrow(workspaceId);
        assertCanManageWorkspace(user, workspace);

        workspace.setTitle(request.getTitle().trim());
        workspace.setDescription(request.getDescription());
        return mapToResponse(workspaceRepository.save(workspace), user);
    }

    @Override
    @Transactional
    public void deleteWorkspace(String email, Long workspaceId) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        Workspace workspace = getWorkspaceOrThrow(workspaceId);
        if (!workspace.getOwner().getId().equals(user.getId())) {
            throw new RuntimeException("Chỉ chủ sở hữu mới có thể xóa workspace này");
        }
        workspaceInviteRepository.deleteByWorkspaceId(workspaceId);
        workspaceMemberRepository.deleteByWorkspaceId(workspaceId);
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
                result.add(mapToResponse(w, user));
            }
        }
        for (WorkspaceMember m : workspaceMemberRepository.findByUserId(user.getId())) {
            if (seen.add(m.getWorkspace().getId())) {
                result.add(mapToResponse(m.getWorkspace(), user));
            }
        }
        return result;
    }

    @Override
    @Transactional
    public WorkspaceMemberResponse joinByInviteCode(String email, JoinWorkspaceByCodeRequest request) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        String code = request.getInviteCode().trim().toUpperCase();
        Workspace workspace = workspaceRepository.findByInviteCode(code)
                .orElseThrow(() -> new RuntimeException("Mã mời không hợp lệ"));

        if (workspaceMemberRepository.findByWorkspaceIdAndUserId(workspace.getId(), user.getId()).isPresent()) {
            throw new RuntimeException("Bạn đã là thành viên của workspace này");
        }

        assertMemberLimitNotExceeded(workspace);

        WorkspaceMember member = workspaceMemberRepository.save(WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(WorkspaceRole.MEMBER)
                .isImportant(false)
                .build());
        return mapToMemberResponse(member);
    }

    @Override
    @Transactional
    public WorkspaceResponse setImportant(String email, Long workspaceId, UpdateMemberImportantRequest request) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, user.getId())
                .orElseThrow(() -> new RuntimeException("You are not a member of this workspace"));
        member.setIsImportant(Boolean.TRUE.equals(request.getImportant()));
        workspaceMemberRepository.save(member);
        return mapToResponse(getWorkspaceOrThrow(workspaceId), user);
    }

    @Override
    @Transactional
    public InviteMemberResultResponse inviteMember(String currentEmail, Long workspaceId, InviteMemberRequest request) {
        User currentUser = userRepository.findByEmail(currentEmail).orElseThrow(() -> new RuntimeException("User not found"));
        Workspace workspace = getWorkspaceOrThrow(workspaceId);
        assertCanManageWorkspace(currentUser, workspace);

        String inviteEmail = request.getEmail().trim().toLowerCase();
        if (inviteEmail.equals(currentUser.getEmail().toLowerCase())) {
            throw new RuntimeException("Bạn không thể mời chính mình");
        }
        if (inviteEmail.equals(workspace.getOwner().getEmail().toLowerCase())) {
            throw new RuntimeException("Chủ sở hữu đã có trong workspace");
        }

        assertMemberLimitNotExceeded(workspace);

        WorkspaceRole role = request.getRole() != null ? request.getRole() : WorkspaceRole.MEMBER;
        if (role == WorkspaceRole.OWNER) {
            throw new RuntimeException("Không thể mời quyền OWNER");
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
                    .role(WorkspaceRole.MEMBER)
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
                .role(WorkspaceRole.MEMBER)
                .token(token)
                .status(InviteStatus.PENDING)
                .invitedBy(currentUser)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build());

        String inviteLink = buildEmailInviteLink(token);
        emailService.sendWorkspaceInviteEmail(
                inviteEmail,
                workspace.getTitle(),
                currentUser.getFullName(),
                inviteLink,
                WorkspaceRole.MEMBER.name());

        return InviteMemberResultResponse.builder()
                .resultType("INVITE_SENT")
                .message("Đã gửi lời mời qua email.")
                .invite(mapToInviteResponse(invite))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceShareResponse getWorkspaceShare(String email, Long workspaceId) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        Workspace workspace = getWorkspaceOrThrow(workspaceId);
        assertIsMember(user, workspaceId);
        workspace = ensureWorkspaceQrCode(workspace);

        boolean canManage = canManageWorkspace(user, workspace);
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
                .workspaceName(workspace.getTitle())
                .ownerId(workspace.getOwner().getId())
                .ownerName(workspace.getOwner().getFullName())
                .inviteCode(workspace.getInviteCode())
                .inviteLink(buildJoinLink(workspace.getInviteCode()))
                .qrCodeUrl(workspace.getQrCodeUrl())
                .members(members)
                .pendingInvites(pending)
                .canManageMembers(canManage)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceInviteResponse> getPendingInvites(String email, Long workspaceId) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        Workspace workspace = getWorkspaceOrThrow(workspaceId);
        assertCanManageWorkspace(user, workspace);
        return workspaceInviteRepository.findByWorkspaceIdAndStatus(workspaceId, InviteStatus.PENDING).stream()
                .map(this::mapToInviteResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void revokeInvite(String email, Long workspaceId, Long inviteId) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        Workspace workspace = getWorkspaceOrThrow(workspaceId);
        assertCanManageWorkspace(user, workspace);
        WorkspaceInvite invite = workspaceInviteRepository.findById(inviteId)
                .orElseThrow(() -> new RuntimeException("Invite not found"));
        if (!invite.getWorkspace().getId().equals(workspaceId)) {
            throw new RuntimeException("Invite not found");
        }
        invite.setStatus(InviteStatus.REVOKED);
        workspaceInviteRepository.save(invite);
    }

    @Override
    @Transactional
    public WorkspaceMemberResponse acceptInvite(String email, AcceptInviteRequest request) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        WorkspaceInvite invite = workspaceInviteRepository.findByToken(request.getToken())
                .orElseThrow(() -> new RuntimeException("Lời mời không hợp lệ"));

        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new RuntimeException("Lời mời đã hết hạn hoặc đã được sử dụng");
        }
        if (invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(LocalDateTime.now())) {
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
            return mapToMemberResponse(
                    workspaceMemberRepository.findByWorkspaceIdAndUserId(workspace.getId(), user.getId()).get());
        }

        assertMemberLimitNotExceeded(workspace);

        WorkspaceMember member = workspaceMemberRepository.save(WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(WorkspaceRole.MEMBER)
                .build());
        invite.setStatus(InviteStatus.ACCEPTED);
        workspaceInviteRepository.save(invite);
        return mapToMemberResponse(member);
    }

    @Override
    @Transactional
    public void removeMember(String email, Long workspaceId, Long memberId) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        Workspace workspace = getWorkspaceOrThrow(workspaceId);
        assertCanManageWorkspace(user, workspace);

        WorkspaceMember member = workspaceMemberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));
        if (!member.getWorkspace().getId().equals(workspaceId)) {
            throw new RuntimeException("Member not found");
        }
        if (member.getUser().getId().equals(workspace.getOwner().getId())) {
            throw new RuntimeException("Không thể xóa chủ sở hữu");
        }
        workspaceMemberRepository.delete(member);
    }

    @Override
    @Transactional
    public WorkspaceMemberResponse updateMemberRole(String email, Long workspaceId, Long memberId, UpdateMemberRoleRequest request) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        Workspace workspace = getWorkspaceOrThrow(workspaceId);
        assertCanManageWorkspace(user, workspace);

        WorkspaceMember member = workspaceMemberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));
        if (!member.getWorkspace().getId().equals(workspaceId)) {
            throw new RuntimeException("Member not found");
        }
        if (request.getRole() == WorkspaceRole.OWNER) {
            throw new RuntimeException("Không thể gán quyền OWNER qua API này");
        }
        member.setRole(WorkspaceRole.MEMBER);
        return mapToMemberResponse(workspaceMemberRepository.save(member));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceMemberResponse> getWorkspaceMembers(String email, Long workspaceId) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        assertIsMember(user, workspaceId);
        return workspaceMemberRepository.findByWorkspaceId(workspaceId).stream()
                .map(this::mapToMemberResponse)
                .collect(Collectors.toList());
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

    private boolean canManageWorkspace(User user, Workspace workspace) {
        return workspace.getOwner().getId().equals(user.getId());
    }

    private void assertCanManageWorkspace(User user, Workspace workspace) {
        if (!canManageWorkspace(user, workspace)) {
            throw new RuntimeException("Bạn không có quyền quản lý workspace này");
        }
    }

    private void assertWorkspaceCreateAllowed(User user) {
        Subscription activeSub = subscriptionRepository
                .findActiveRecurringByUserId(user.getId(), SubscriptionStatus.ACTIVE)
                .stream()
                .findFirst()
                .orElse(null);
        String planName = (activeSub != null && activeSub.getPlan() != null) ? activeSub.getPlan().getName() : "Free";
        int count = workspaceRepository.countByOwnerId(user.getId());
        if (planName.equalsIgnoreCase("Free") && count >= 1) {
            throw new RuntimeException("Tài khoản Free chỉ có tối đa 1 Workspace. Hãy nâng cấp!");
        }
        if (planName.equalsIgnoreCase("Student") && count >= 3) {
            throw new RuntimeException("Sinh viên được tạo tối đa 3 Workspace!");
        }
    }

    private void assertMemberLimitNotExceeded(Workspace workspace) {
        User owner = workspace.getOwner();
        Subscription activeSub = subscriptionRepository
                .findActiveRecurringByUserId(owner.getId(), SubscriptionStatus.ACTIVE)
                .stream()
                .findFirst()
                .orElse(null);
        String planName = (activeSub != null && activeSub.getPlan() != null) ? activeSub.getPlan().getName() : "Free";
        long count = workspaceMemberRepository.countByWorkspaceId(workspace.getId());
        if (planName.equalsIgnoreCase("Free") && count >= 2) {
            throw new RuntimeException("Gói Free chỉ hỗ trợ tối đa 2 thành viên mỗi workspace");
        }
        if (planName.equalsIgnoreCase("Student") && count >= 5) {
            throw new RuntimeException("Gói Student hỗ trợ tối đa 5 thành viên mỗi workspace");
        }
    }

    private String generateUniqueInviteCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String code = "WLA-" + randomSegment(3) + "-" + randomSegment(3);
            if (!workspaceRepository.existsByInviteCode(code)) {
                return code;
            }
        }
        throw new RuntimeException("Không thể tạo mã mời, vui lòng thử lại");
    }

    private String randomSegment(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(INVITE_ALPHABET.charAt(RANDOM.nextInt(INVITE_ALPHABET.length())));
        }
        return sb.toString();
    }

    private String buildJoinLink(String inviteCode) {
        return frontendUrl.replaceAll("/$", "") + "/projects?code=" + inviteCode;
    }

    private String uploadInviteQrCode(String inviteLink, String inviteCode) {
        try {
            byte[] png = QrCodeGenerator.generatePng(inviteLink, 512);
            String prefix = qrObjectPrefix.endsWith("/") ? qrObjectPrefix : qrObjectPrefix + "/";
            String safeCode = inviteCode.replaceAll("[^A-Za-z0-9]", "");
            String objectKey = prefix + safeCode + "-" + UUID.randomUUID().toString().substring(0, 8) + ".png";
            return fileService.uploadRawBytes(png, objectKey, "image/png");
        } catch (Exception e) {
            log.warn("QR upload failed for workspace invite {}, fallback to link: {}", inviteCode, e.getMessage());
            return inviteLink;
        }
    }

    private boolean isStoredQrImageUrl(String url) {
        return url != null
                && url.contains("/api/files/download/")
                && url.toLowerCase().contains(".png");
    }

    private Workspace ensureWorkspaceQrCode(Workspace workspace) {
        if (isStoredQrImageUrl(workspace.getQrCodeUrl())) {
            return workspace;
        }
        String inviteLink = buildJoinLink(workspace.getInviteCode());
        String qrUrl = uploadInviteQrCode(inviteLink, workspace.getInviteCode());
        workspace.setQrCodeUrl(qrUrl);
        return workspaceRepository.save(workspace);
    }

    private String buildEmailInviteLink(String token) {
        return frontendUrl.replaceAll("/$", "") + "/projects?inviteToken=" + token;
    }

    private WorkspaceResponse mapToResponse(Workspace workspace, User viewer) {
        Boolean important = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspace.getId(), viewer.getId())
                .map(WorkspaceMember::getIsImportant)
                .orElse(false);
        return WorkspaceResponse.builder()
                .id(workspace.getId())
                .title(workspace.getTitle())
                .description(workspace.getDescription())
                .ownerId(workspace.getOwner().getId())
                .ownerName(workspace.getOwner().getFullName())
                .inviteCode(workspace.getInviteCode())
                .inviteLink(buildJoinLink(workspace.getInviteCode()))
                .qrCodeUrl(workspace.getQrCodeUrl())
                .isImportant(important)
                .createdAt(workspace.getCreatedAt())
                .updatedAt(workspace.getUpdatedAt())
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
                .isImportant(member.getIsImportant())
                .joinedAt(member.getJoinedAt())
                .build();
    }

    private WorkspaceInviteResponse mapToInviteResponse(WorkspaceInvite invite) {
        return WorkspaceInviteResponse.builder()
                .id(invite.getId())
                .workspaceId(invite.getWorkspace().getId())
                .workspaceName(invite.getWorkspace().getTitle())
                .email(invite.getEmail())
                .role(invite.getRole())
                .status(invite.getStatus())
                .invitedByName(invite.getInvitedBy() != null ? invite.getInvitedBy().getFullName() : null)
                .inviteLink(buildEmailInviteLink(invite.getToken()))
                .expiresAt(invite.getExpiresAt())
                .createdAt(invite.getCreatedAt())
                .build();
    }
}
