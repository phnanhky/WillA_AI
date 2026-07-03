package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.WorkspaceInvite;
import com.willa.ai.backend.entity.enums.InviteStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceInviteRepository extends JpaRepository<WorkspaceInvite, Long> {
    List<WorkspaceInvite> findByWorkspaceIdAndStatus(Long workspaceId, InviteStatus status);
    Optional<WorkspaceInvite> findByToken(String token);

    @Query("""
            SELECT i FROM WorkspaceInvite i
            JOIN FETCH i.workspace
            JOIN FETCH i.invitedBy
            WHERE i.token = :token
            """)
    Optional<WorkspaceInvite> findByTokenWithDetails(@Param("token") String token);
    Optional<WorkspaceInvite> findByTokenAndStatus(String token, InviteStatus status);
    Optional<WorkspaceInvite> findByWorkspaceIdAndEmailAndStatus(Long workspaceId, String email, InviteStatus status);

    @Modifying
    @Query("DELETE FROM WorkspaceInvite i WHERE i.workspace.id = :workspaceId")
    void deleteByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
