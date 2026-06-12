package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.TaskChecklist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskChecklistRepository extends JpaRepository<TaskChecklist, Long> {
    List<TaskChecklist> findByTaskIdOrderByPositionAscIdAsc(Long taskId);

    Optional<TaskChecklist> findByIdAndTaskId(Long id, Long taskId);
}
