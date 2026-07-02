package com.willa.ai.backend.service;

import com.willa.ai.backend.entity.Task;
import com.willa.ai.backend.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkspaceQueryHandlerService {

    private final TaskRepository taskRepository;

    public List<Task> getWorkspaceTasks(Long workspaceId) {
        return taskRepository.findByWorkspaceIdWithAssignees(workspaceId);
    }
}
