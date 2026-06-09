package com.willa.ai.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.willa.ai.backend.entity.Workspace;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
    List<Workspace> findByOwnerId(Long ownerId);

    int countByOwnerId(Long ownerId);

    Optional<Workspace> findByInviteCode(String inviteCode);

    boolean existsByInviteCode(String inviteCode);
}
