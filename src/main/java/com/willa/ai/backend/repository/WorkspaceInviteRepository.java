package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.WorkspaceInvite;
import com.willa.ai.backend.entity.enums.InviteStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceInviteRepository extends JpaRepository<WorkspaceInvite, Long> {
    List<WorkspaceInvite> findByWorkspaceIdAndStatus(Long workspaceId, InviteStatus status);
    Optional<WorkspaceInvite> findByTokenAndStatus(String token, InviteStatus status);
    Optional<WorkspaceInvite> findByWorkspaceIdAndEmailAndStatus(Long workspaceId, String email, InviteStatus status);
    void deleteByWorkspaceId(Long workspaceId);
}
