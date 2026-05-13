package com.willa.ai.backend.service.impl;

import com.willa.ai.backend.dto.request.WorkspaceRequest;
import com.willa.ai.backend.dto.request.InviteMemberRequest;
import com.willa.ai.backend.dto.response.WorkspaceResponse;
import com.willa.ai.backend.dto.response.WorkspaceMemberResponse;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.Workspace;
import com.willa.ai.backend.entity.WorkspaceMember;
import com.willa.ai.backend.entity.enums.WorkspaceRole;
import com.willa.ai.backend.entity.Subscription;
import com.willa.ai.backend.entity.enums.SubscriptionStatus;
import com.willa.ai.backend.repository.SubscriptionRepository;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.repository.WorkspaceMemberRepository;
import com.willa.ai.backend.repository.WorkspaceRepository;
import com.willa.ai.backend.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkspaceServiceImpl implements WorkspaceService {
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;

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
    @Transactional(readOnly = true)
    public List<WorkspaceResponse> getUserWorkspaces(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        return workspaceRepository.findByOwnerId(user.getId()).stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public WorkspaceMemberResponse inviteMember(String currentEmail, Long workspaceId, InviteMemberRequest request) {
        User currentUser = userRepository.findByEmail(currentEmail).orElseThrow(() -> new RuntimeException("User not found"));
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow(() -> new RuntimeException("Workspace not found"));
        
        // Check if current user is ADMIN
        WorkspaceMember currentMember = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, currentUser.getId())
            .orElseThrow(() -> new RuntimeException("You are not a member of this workspace"));
            
        if (currentMember.getRole() != WorkspaceRole.ADMIN) {
            throw new RuntimeException("Only ADMINs can invite new members");
        }
        
        // Plan limits check for team size
        User owner = workspace.getOwner();
        Subscription activeSub = subscriptionRepository.findByUserIdAndStatus(owner.getId(), SubscriptionStatus.ACTIVE).stream().findFirst().orElse(null);
        String planName = (activeSub != null && activeSub.getPlan() != null) ? activeSub.getPlan().getName() : "Free";
        
        long memberCount = workspaceMemberRepository.countByWorkspaceId(workspaceId);
        if (planName.equalsIgnoreCase("Free") && memberCount >= 2) {
            throw new RuntimeException("Tài khoản Free chỉ cho phép tối đa 2 người trong 1 Workspace!");
        } else if (planName.equalsIgnoreCase("Student") && memberCount >= 5) {
            throw new RuntimeException("Sinh viên chỉ cho phép tối đa 5 người trong 1 Workspace!");
        }
        
        User invitee = userRepository.findByEmail(request.getEmail()).orElseThrow(() -> new RuntimeException("Invited user not found"));
        
        if (workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, invitee.getId()).isPresent()) {
            throw new RuntimeException("User is already a member of this workspace");
        }
        
        WorkspaceMember newMember = workspaceMemberRepository.save(WorkspaceMember.builder()
            .workspace(workspace)
            .user(invitee)
            .role(request.getRole() != null ? request.getRole() : WorkspaceRole.VIEWER)
            .build());
            
        return mapToMemberResponse(newMember);
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

    private WorkspaceResponse mapToResponse(Workspace workspace) {
        return WorkspaceResponse.builder().id(workspace.getId()).name(workspace.getName()).description(workspace.getDescription()).ownerId(workspace.getOwner().getId()).ownerName(workspace.getOwner().getFullName()).createdAt(workspace.getCreatedAt()).build();
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
}
