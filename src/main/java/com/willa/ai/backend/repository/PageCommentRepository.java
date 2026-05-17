package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.PageComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PageCommentRepository extends JpaRepository<PageComment, Long> {
    List<PageComment> findByWorkspacePageIdOrderByCreatedAtAsc(Long workspacePageId);
    void deleteByWorkspacePageId(Long workspacePageId);
}
