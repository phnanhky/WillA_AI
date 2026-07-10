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
import com.willa.ai.backend.dto.response.WorkspaceInvitePreviewResponse;
import com.willa.ai.backend.dto.response.WorkspaceInviteResponse;
import com.willa.ai.backend.dto.response.WorkspaceMemberResponse;
import com.willa.ai.backend.dto.response.WorkspaceResponse;
import com.willa.ai.backend.dto.response.WorkspaceShareResponse;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.Workspace;
import com.willa.ai.backend.entity.WorkspaceInvite;
import com.willa.ai.backend.entity.WorkspaceMember;
import com.willa.ai.backend.entity.enums.InviteStatus;
import com.willa.ai.backend.entity.enums.WorkspaceJoinSource;
import com.willa.ai.backend.entity.enums.WorkspaceRole;
import com.willa.ai.backend.entity.enums.WorkflowType;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.repository.WorkspaceInviteRepository;
import com.willa.ai.backend.repository.WorkspaceMemberRepository;
import com.willa.ai.backend.repository.WorkspaceRepository;
import com.willa.ai.backend.service.EmailService;
import com.willa.ai.backend.service.FileService;
import com.willa.ai.backend.service.WorkspaceChannelService;
import com.willa.ai.backend.service.WorkspaceDataPurger;
import com.willa.ai.backend.service.WorkspacePlanService;
import com.willa.ai.backend.service.WorkspaceRealtimeService;
import com.willa.ai.backend.service.WorkspaceService;
import com.willa.ai.backend.service.WorkflowUsageService;
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
    private final WorkspaceDataPurger workspaceDataPurger;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final FileService fileService;
    private final WorkspaceRealtimeService workspaceRealtimeService;
    private final WorkspaceChannelService workspaceChannelService;
    private final WorkspacePlanService workspacePlanService;
    private final WorkflowUsageService workflowUsageService;

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
        String inviteLink = buildQrJoinLink(inviteCode);
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
                .joinSource(WorkspaceJoinSource.OWNER)
                .build());

        workspaceChannelService.ensureDefaultChannels(workspace);

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
        WorkspaceResponse response = mapToResponse(workspaceRepository.save(workspace), user);
        workspaceRealtimeService.publishWorkspaceChanged(workspaceId);
        return response;
    }

    @Override
    @Transactional
    public void deleteWorkspace(String email, Long workspaceId) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        Workspace workspace = workspaceRepository.findByIdWithOwner(workspaceId)
                .orElseThrow(() -> new RuntimeException("Workspace not found"));
        Long ownerId = workspace.getOwner().getId();
        if (!ownerId.equals(user.getId())) {
            log.warn("deleteWorkspace denied workspaceId={} userId={} ownerId={}", workspaceId, user.getId(), ownerId);
            throw new RuntimeException("Chỉ chủ sở hữu mới có thể xóa workspace này");
        }
        try {
            workspaceDataPurger.purgeWorkspaceTaskData(workspaceId);
            workspaceInviteRepository.deleteByWorkspaceId(workspaceId);
            workspaceMemberRepository.deleteByWorkspaceId(workspaceId);
            workspaceRepository.delete(workspace);
            log.info("Deleted workspace id={} by user id={}", workspaceId, user.getId());
        } catch (RuntimeException e) {
            log.error("deleteWorkspace failed workspaceId={}: {}", workspaceId, e.getMessage(), e);
            throw new RuntimeException("Không thể xóa workspace: " + rootMessage(e), e);
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return msg != null && !msg.isBlank() ? msg : e.getClass().getSimpleName();
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
            return mapToMemberResponse(
                    workspaceMemberRepository.findByWorkspaceIdAndUserId(workspace.getId(), user.getId()).get());
        }

        assertMemberLimitNotExceeded(workspace);

        WorkspaceJoinSource source = parseJoinSource(request.getJoinSource());
        LocalDateTime now = LocalDateTime.now();
        WorkspaceMember member = workspaceMemberRepository.save(WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(WorkspaceRole.MEMBER)
                .isImportant(false)
                .joinSource(source)
                .firstActiveAt(now)
                .lastActiveAt(now)
                .build());
        workspaceRealtimeService.publishWorkspaceChanged(workspace.getId());
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

        String rawEmail = request.getEmail();
        if (rawEmail == null || rawEmail.isBlank()) {
            throw new RuntimeException("Vui lòng nhập email để gửi lời mời");
        }
        String inviteEmail = rawEmail.trim().toLowerCase();
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
        }
        // Luôn gửi email mời — kể cả email chưa có tài khoản trong hệ thống.

        var pendingOpt = workspaceInviteRepository.findByWorkspaceIdAndEmailAndStatus(
                workspaceId, inviteEmail, InviteStatus.PENDING);
        if (pendingOpt.isPresent()) {
            WorkspaceInvite existing = pendingOpt.get();
            if (existing.getExpiresAt() != null && existing.getExpiresAt().isBefore(LocalDateTime.now())) {
                existing.setStatus(InviteStatus.EXPIRED);
                workspaceInviteRepository.save(existing);
            } else {
                String inviteLink = buildEmailInviteLink(existing.getToken());
                emailService.sendWorkspaceInviteEmail(
                        inviteEmail,
                        workspace.getTitle(),
                        currentUser.getFullName(),
                        inviteLink,
                        role.name());
                return InviteMemberResultResponse.builder()
                        .resultType("INVITE_SENT")
                        .message("Đã gửi lại email mời tới " + inviteEmail
                                + " — người nhận có thể tạo tài khoản mới qua link nếu chưa có.")
                        .invite(mapToInviteResponse(existing))
                        .build();
            }
        }

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

        String inviteLink = buildEmailInviteLink(token);
        emailService.sendWorkspaceInviteEmail(
                inviteEmail,
                workspace.getTitle(),
                currentUser.getFullName(),
                inviteLink,
                role.name());

        return InviteMemberResultResponse.builder()
                .resultType("INVITE_SENT")
                .message("Đã gửi email mời tới " + inviteEmail
                        + " — người nhận có thể tạo tài khoản mới qua link nếu chưa có.")
                .invite(mapToInviteResponse(invite))
                .build();
    }

    @Override
    @Transactional
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
                .joinSource(WorkspaceJoinSource.EMAIL)
                .build());
        invite.setStatus(InviteStatus.ACCEPTED);
        workspaceInviteRepository.save(invite);
        workspaceRealtimeService.publishWorkspaceChanged(workspace.getId());
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
        workspaceRealtimeService.publishWorkspaceChanged(workspaceId);
    }

    @Override
    @Transactional
    public void leaveWorkspace(String email, Long workspaceId) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        Workspace workspace = getWorkspaceOrThrow(workspaceId);
        if (workspace.getOwner().getId().equals(user.getId())) {
            throw new RuntimeException("Chủ sở hữu không thể rời workspace. Hãy xóa workspace hoặc chuyển quyền.");
        }
        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, user.getId())
                .orElseThrow(() -> new RuntimeException("Bạn không phải thành viên của workspace này"));
        workspaceMemberRepository.delete(member);
        workspaceRealtimeService.publishWorkspaceChanged(workspaceId);
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

    @Override
    @Transactional
    public void recordMemberActivity(String email, Long workspaceId) {
        try {
            User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
            WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, user.getId())
                    .orElse(null);
            if (member == null) {
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            if (member.getFirstActiveAt() == null) {
                member.setFirstActiveAt(now);
            }
            member.setLastActiveAt(now);
            workspaceMemberRepository.save(member);
        } catch (Exception e) {
            log.warn("recordMemberActivity skipped workspaceId={}: {}", workspaceId, e.getMessage());
        }
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
        int count = workspaceRepository.countByOwnerId(user.getId());
        int max = workspacePlanService.maxOwnedWorkspaces(user);
        if (count >= max) {
            throw new RuntimeException("Gói " + workspacePlanService.displayNameForUser(user)
                    + " chỉ có tối đa " + (max == Integer.MAX_VALUE ? "không giới hạn" : max)
                    + " workspace. Hãy nâng cấp gói workspace!");
        }
    }

    private void assertMemberLimitNotExceeded(Workspace workspace) {
        User owner = workspace.getOwner();
        long count = workspaceMemberRepository.countByWorkspaceId(workspace.getId());
        int max = workspacePlanService.maxMembersPerWorkspace(owner);
        if (count >= max) {
            throw new RuntimeException("Gói " + workspacePlanService.displayNameForUser(owner)
                    + " chỉ hỗ trợ tối đa " + (max == Integer.MAX_VALUE ? "không giới hạn" : max)
                    + " thành viên mỗi workspace");
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

    private String buildQrJoinLink(String inviteCode) {
        return buildJoinLink(inviteCode) + "&src=qr";
    }

    private WorkspaceJoinSource parseJoinSource(String raw) {
        if (raw == null || raw.isBlank()) {
            return WorkspaceJoinSource.CODE;
        }
        try {
            return WorkspaceJoinSource.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return WorkspaceJoinSource.CODE;
        }
    }

    private String uploadInviteQrCode(String inviteLink, String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) {
            throw new IllegalArgumentException("inviteCode is required");
        }
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
        if (workspace.getInviteCode() == null || workspace.getInviteCode().isBlank()) {
            workspace.setInviteCode(generateUniqueInviteCode());
            workspace = workspaceRepository.save(workspace);
        }
        if (isStoredQrImageUrl(workspace.getQrCodeUrl())) {
            return workspace;
        }
        String inviteLink = buildQrJoinLink(workspace.getInviteCode());
        String qrUrl = uploadInviteQrCode(inviteLink, workspace.getInviteCode());
        workspace.setQrCodeUrl(qrUrl);
        return workspaceRepository.save(workspace);
    }

    private String buildEmailInviteLink(String token) {
        return frontendUrl.replaceAll("/$", "") + "/join-workspace?token=" + token;
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceInvitePreviewResponse getInvitePreview(String token) {
        if (token == null || token.isBlank()) {
            return WorkspaceInvitePreviewResponse.builder()
                    .valid(false)
                    .message("Token không hợp lệ")
                    .build();
        }
        return workspaceInviteRepository.findByTokenWithDetails(token.trim())
                .map(invite -> {
                    boolean expired = invite.getExpiresAt() != null
                            && invite.getExpiresAt().isBefore(LocalDateTime.now());
                    boolean pending = invite.getStatus() == InviteStatus.PENDING;
                    boolean valid = pending && !expired;
                    String message = valid
                            ? null
                            : expired
                                    ? "Lời mời đã hết hạn"
                                    : "Lời mời không còn hiệu lực";
                    return WorkspaceInvitePreviewResponse.builder()
                            .valid(valid)
                            .expired(expired)
                            .workspaceId(invite.getWorkspace().getId())
                            .workspaceName(invite.getWorkspace().getTitle())
                            .inviterName(invite.getInvitedBy().getFullName())
                            .inviteEmail(invite.getEmail())
                            .message(message)
                            .build();
                })
                .orElse(WorkspaceInvitePreviewResponse.builder()
                        .valid(false)
                        .message("Lời mời không hợp lệ")
                        .build());
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
                .avatarUrl(member.getUser().getAvatarUrl())
                .email(member.getUser().getEmail())
                .role(member.getRole())
                .isImportant(member.getIsImportant())
                .joinedAt(member.getJoinedAt())
                .joinSource(member.getJoinSource())
                .firstActiveAt(member.getFirstActiveAt())
                .lastActiveAt(member.getLastActiveAt())
                .activated(member.getFirstActiveAt() != null)
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

    @Value("${ai.xai.api-key:}")
    private String xaiApiKey;

    @Override
    @Transactional(readOnly = true)
    public com.willa.ai.backend.dto.response.WorkspaceChatExtractResponse extractTaskFromChat(String email, Long workspaceId, com.willa.ai.backend.dto.request.WorkspaceChatExtractRequest request) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        assertIsMember(user, workspaceId);

        List<WorkspaceMember> members = workspaceMemberRepository.findByWorkspaceId(workspaceId);
        String membersNames = members.stream()
                .map(m -> m.getUser().getFullName() + " (Email: " + m.getUser().getEmail() + ", ID: " + m.getUser().getId() + ")")
                .collect(Collectors.joining(", "));

        String systemPrompt = "Tin nhắn chat: \"" + request.getMessage() + "\"\n"
                + "Thành viên trong workspace: " + membersNames + "\n"
                + "Hôm nay là ngày: " + request.getCurrentDate() + "\n"
                + "Nếu tin nhắn này chứa một hoặc nhiều task được giao (có người được giao + việc cần làm), trả về JSON. Nếu không có task nào, trả về {\"tasks\": []}.\n"
                + "Dưới đây là danh sách các cột (status) hiện có trong workspace (định dạng Tên cột (STATUS_CODE)): " + (request.getLists() != null ? request.getLists() : "TODO") + "\n"
                + "Nếu người dùng nhắc đến tên cột/danh sách (ví dụ Cần làm, Biz...), hãy trích xuất mã STATUS_CODE tương ứng vào trường 'status'. Nếu không rõ thì mặc định là 'TODO'.\n"
                + "ĐẶC BIỆT LƯU Ý: Nếu người dùng yêu cầu tạo thêm danh sách, tạo cột mới (ví dụ 'thêm danh sách thiết kế', 'tạo cột Thiết Kế'), BẮT BUỘC phải trả về mảng 'newLists' chứa tên các danh sách/cột đó.\n"
                + "Nếu người dùng VỪA yêu cầu tạo cột VỪA giao việc, bạn phải trả về CẢ 'newLists' VÀ 'tasks'. Nếu giao việc cho cột mới đó, hãy gán 'status' bằng CHÍNH XÁC tên của cột mới đó (ví dụ 'Thiết Kế').\n"
                + "Format JSON phải có cấu trúc sau (các trường không có dữ liệu thì để mảng rỗng []):\n"
                + "{\n"
                + "  \"tasks\": [\n"
                + "    { \"assigneeUserId\": <ID>, \"assignee\": \"tên\", \"task\": \"mô tả\", \"deadline\": \"YYYY-MM-DDT23:59:59\", \"priority\": \"URGENT/HIGH/MEDIUM/LOW/NONE\", \"status\": \"STATUS_CODE\", \"checklists\": [{ \"title\": \"việc 1\", \"assigneeUserId\": <ID>, \"deadline\": \"YYYY-MM-DDT23:59:59\", \"priority\": \"URGENT/HIGH/MEDIUM/LOW/NONE\" }] }\n"
                + "  ],\n"
                + "  \"newLists\": [\"tên danh sách mới\"]\n"
                + "}\n"
                + "Chú ý: Cố gắng map tên người nhận với assigneeUserId dựa vào danh sách thành viên. Nếu không map được thì để assigneeUserId là null. "
                + "Chỉ trả JSON, không giải thích gì thêm, không markdown.";

        if (xaiApiKey == null || xaiApiKey.isBlank()) {
            throw new RuntimeException("XAI API Key is not configured.");
        }

        return workflowUsageService.track(user, WorkflowType.WORKSPACE, null, () -> {
            try {
                org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                headers.setBearerAuth(xaiApiKey);

                java.util.Map<String, Object> body = new java.util.HashMap<>();
                body.put("model", "grok-build-0.1");

                java.util.List<java.util.Map<String, String>> messages = new java.util.ArrayList<>();
                java.util.Map<String, String> msg = new java.util.HashMap<>();
                msg.put("role", "user");
                msg.put("content", systemPrompt);
                messages.add(msg);

                body.put("messages", messages);
                body.put("temperature", 0.0);

                org.springframework.http.HttpEntity<java.util.Map<String, Object>> entity =
                        new org.springframework.http.HttpEntity<>(body, headers);

                org.springframework.http.ResponseEntity<String> response = restTemplate.postForEntity(
                        "https://api.x.ai/v1/chat/completions", entity, String.class);

                String respBody = response.getBody();
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode rootNode = mapper.readTree(respBody);

                String content = rootNode.path("choices").get(0).path("message").path("content").asText("");
                log.info("AI response: {}", content);
                content = content.trim();
                if (content.startsWith("```json")) {
                    content = content.substring(7);
                }
                if (content.startsWith("```")) {
                    content = content.substring(3);
                }
                if (content.endsWith("```")) {
                    content = content.substring(0, content.length() - 3);
                }

                return mapper.readValue(content.trim(), com.willa.ai.backend.dto.response.WorkspaceChatExtractResponse.class);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                log.error("Failed to extract task from chat: ", e);
                throw new RuntimeException("Lỗi khi phân tích bằng AI: " + e.getMessage());
            }
        });
    }
}
