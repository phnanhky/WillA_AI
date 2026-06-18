package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.WorkspaceDmConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkspaceDmConversationRepository extends JpaRepository<WorkspaceDmConversation, Long> {
    @Query("""
            SELECT c FROM WorkspaceDmConversation c
            WHERE c.workspace.id = :workspaceId
              AND ((c.userA.id = :userId1 AND c.userB.id = :userId2)
                OR (c.userA.id = :userId2 AND c.userB.id = :userId1))
            """)
    Optional<WorkspaceDmConversation> findByWorkspaceAndUsers(
            @Param("workspaceId") Long workspaceId,
            @Param("userId1") Long userId1,
            @Param("userId2") Long userId2);

    @Query("""
            SELECT c FROM WorkspaceDmConversation c
            WHERE c.workspace.id = :workspaceId
              AND (c.userA.id = :userId OR c.userB.id = :userId)
            ORDER BY c.createdAt DESC
            """)
    List<WorkspaceDmConversation> findByWorkspaceIdAndParticipant(@Param("workspaceId") Long workspaceId,
            @Param("userId") Long userId);

    Optional<WorkspaceDmConversation> findByIdAndWorkspaceId(Long id, Long workspaceId);
}
