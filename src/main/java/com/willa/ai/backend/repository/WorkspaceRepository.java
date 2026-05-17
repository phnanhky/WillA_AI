package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
    List<Workspace> findByOwnerId(Long ownerId);
    int countByOwnerId(Long ownerId);
    Page<Workspace> findByIsPublicTrue(Pageable pageable);
}
