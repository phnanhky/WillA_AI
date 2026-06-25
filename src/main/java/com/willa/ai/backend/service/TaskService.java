package com.willa.ai.backend.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.willa.ai.backend.dto.request.TaskChecklistItemRequest;
import com.willa.ai.backend.dto.request.TaskChecklistRequest;
import com.willa.ai.backend.dto.request.TaskCommentRequest;
import com.willa.ai.backend.dto.request.TaskRequest;
import com.willa.ai.backend.dto.response.TaskAttachmentResponse;
import com.willa.ai.backend.dto.response.TaskChecklistItemResponse;
import com.willa.ai.backend.dto.response.TaskChecklistResponse;
import com.willa.ai.backend.dto.response.TaskCommentResponse;
import com.willa.ai.backend.dto.response.TaskResponse;

public interface TaskService {
    List<TaskResponse> listTasks(String email, Long workspaceId, Long projectId);

    TaskResponse getTask(String email, Long workspaceId, Long taskId);

    TaskResponse createTask(String email, Long workspaceId, TaskRequest request);

    TaskResponse updateTask(String email, Long workspaceId, Long taskId, TaskRequest request);

    void deleteTask(String email, Long workspaceId, Long taskId);

    List<TaskCommentResponse> listComments(String email, Long workspaceId, Long taskId);

    TaskCommentResponse addComment(String email, Long workspaceId, Long taskId, TaskCommentRequest request);

    List<TaskCommentResponse> listHubComments(String email, Long workspaceId);

    TaskAttachmentResponse addAttachment(
            String email, Long workspaceId, Long taskId, MultipartFile file);

    TaskAttachmentResponse updateAttachment(
            String email, Long workspaceId, Long taskId, Long attachmentId, String fileName);

    void deleteAttachment(String email, Long workspaceId, Long taskId, Long attachmentId);

    TaskResponse createGoogleMeet(String email, Long workspaceId, Long taskId);

    TaskResponse removeGoogleMeet(String email, Long workspaceId, Long taskId);

    List<TaskChecklistResponse> listChecklists(String email, Long workspaceId, Long taskId);

    TaskChecklistResponse createChecklist(String email, Long workspaceId, Long taskId, TaskChecklistRequest request);

    TaskChecklistResponse updateChecklist(String email, Long workspaceId, Long taskId, Long checklistId, TaskChecklistRequest request);

    void deleteChecklist(String email, Long workspaceId, Long taskId, Long checklistId);

    TaskChecklistItemResponse addChecklistItem(String email, Long workspaceId, Long taskId, Long checklistId, TaskChecklistItemRequest request);

    TaskChecklistItemResponse updateChecklistItem(String email, Long workspaceId, Long taskId, Long checklistId, Long itemId, TaskChecklistItemRequest request);

    void deleteChecklistItem(String email, Long workspaceId, Long taskId, Long checklistId, Long itemId);
}
