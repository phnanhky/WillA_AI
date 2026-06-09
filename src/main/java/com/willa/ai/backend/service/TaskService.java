package com.willa.ai.backend.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.willa.ai.backend.dto.request.TaskCommentRequest;
import com.willa.ai.backend.dto.request.TaskRequest;
import com.willa.ai.backend.dto.response.TaskAttachmentResponse;
import com.willa.ai.backend.dto.response.TaskCommentResponse;
import com.willa.ai.backend.dto.response.TaskResponse;

public interface TaskService {
    List<TaskResponse> listTasks(String email, Long workspaceId);

    TaskResponse getTask(String email, Long workspaceId, Long taskId);

    TaskResponse createTask(String email, Long workspaceId, TaskRequest request);

    TaskResponse updateTask(String email, Long workspaceId, Long taskId, TaskRequest request);

    void deleteTask(String email, Long workspaceId, Long taskId);

    List<TaskCommentResponse> listComments(String email, Long workspaceId, Long taskId);

    TaskCommentResponse addComment(String email, Long workspaceId, Long taskId, TaskCommentRequest request);

    TaskAttachmentResponse addAttachment(
            String email, Long workspaceId, Long taskId, MultipartFile file);
}
