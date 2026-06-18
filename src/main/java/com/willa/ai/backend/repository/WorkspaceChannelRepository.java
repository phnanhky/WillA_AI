package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.WorkspaceChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkspaceChannelRepository extends JpaRepository<WorkspaceChannel, Long> {
    List<WorkspaceChannel> findByWorkspaceIdOrderByPositionAscIdAsc(Long workspaceId);

    Optional<WorkspaceChannel> findByIdAndWorkspaceId(Long id, Long workspaceId);

    Optional<WorkspaceChannel> findByWorkspaceIdAndNameIgnoreCase(Long workspaceId, String name);

    boolean existsByWorkspaceIdAndNameIgnoreCase(Long workspaceId, String name);
}
