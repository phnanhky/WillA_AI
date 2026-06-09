package com.willa.ai.backend.service.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.willa.ai.backend.dto.request.TaskCommentRequest;
import com.willa.ai.backend.dto.request.TaskRequest;
import com.willa.ai.backend.dto.response.TaskAssigneeResponse;
import com.willa.ai.backend.dto.response.TaskAttachmentResponse;
import com.willa.ai.backend.dto.response.TaskCommentResponse;
import com.willa.ai.backend.dto.response.TaskResponse;
import com.willa.ai.backend.entity.Task;
import com.willa.ai.backend.entity.TaskAttachment;
import com.willa.ai.backend.entity.TaskComment;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.Workspace;
import com.willa.ai.backend.entity.enums.TaskStatus;
import com.willa.ai.backend.repository.TaskAttachmentRepository;
import com.willa.ai.backend.repository.TaskCommentRepository;
import com.willa.ai.backend.repository.TaskRepository;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.repository.WorkspaceMemberRepository;
import com.willa.ai.backend.repository.WorkspaceRepository;
import com.willa.ai.backend.service.FileService;
import com.willa.ai.backend.service.TaskService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final TaskCommentRepository taskCommentRepository;
    private final TaskAttachmentRepository taskAttachmentRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final FileService fileService;

    @Override
    @Transactional(readOnly = true)
    public List<TaskResponse> listTasks(String email, Long workspaceId) {
        User user = requireUser(email);
        assertIsMember(user, workspaceId);
        return taskRepository.findByWorkspaceIdOrderByPositionAscIdAsc(workspaceId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public TaskResponse getTask(String email, Long workspaceId, Long taskId) {
        User user = requireUser(email);
        assertIsMember(user, workspaceId);
        Task task = taskRepository.findByIdAndWorkspaceId(taskId, workspaceId)
                .orElseThrow(() -> new RuntimeException("Task not found"));
        return mapToResponse(task);
    }

    @Override
    @Transactional
    public TaskResponse createTask(String email, Long workspaceId, TaskRequest request) {
        User user = requireUser(email);
        Workspace workspace = getWorkspaceOrThrow(workspaceId);
        assertIsMember(user, workspaceId);

        TaskStatus status = request.getStatus() != null ? request.getStatus() : TaskStatus.TODO;
        int position = request.getPosition() != null
                ? request.getPosition()
                : nextPosition(workspaceId, status);

        Task task = Task.builder()
                .workspace(workspace)
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .status(status)
                .dueDate(request.getDueDate())
                .position(position)
                .assignees(resolveAssignees(workspaceId, request.getAssigneeUserIds()))
                .build();

        return mapToResponse(taskRepository.save(task));
    }

    @Override
    @Transactional
    public TaskResponse updateTask(String email, Long workspaceId, Long taskId, TaskRequest request) {
        User user = requireUser(email);
        assertIsMember(user, workspaceId);
        Task task = taskRepository.findByIdAndWorkspaceId(taskId, workspaceId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            task.setTitle(request.getTitle().trim());
        }
        if (request.getDescription() != null) {
            task.setDescription(request.getDescription());
        }
        if (request.getStatus() != null) {
            task.setStatus(request.getStatus());
        }
        if (request.getDueDate() != null || request.getDueDate() == null) {
            task.setDueDate(request.getDueDate());
        }
        if (request.getPosition() != null) {
            task.setPosition(request.getPosition());
        }
        if (request.getAssigneeUserIds() != null) {
            task.setAssignees(resolveAssignees(workspaceId, request.getAssigneeUserIds()));
        }

        return mapToResponse(taskRepository.save(task));
    }

    @Override
    @Transactional
    public void deleteTask(String email, Long workspaceId, Long taskId) {
        User user = requireUser(email);
        assertIsMember(user, workspaceId);
        Task task = taskRepository.findByIdAndWorkspaceId(taskId, workspaceId)
                .orElseThrow(() -> new RuntimeException("Task not found"));
        taskRepository.delete(task);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskCommentResponse> listComments(String email, Long workspaceId, Long taskId) {
        User user = requireUser(email);
        assertIsMember(user, workspaceId);
        assertTaskInWorkspace(taskId, workspaceId);
        return taskCommentRepository.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
                .map(this::mapComment)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public TaskCommentResponse addComment(String email, Long workspaceId, Long taskId, TaskCommentRequest request) {
        User user = requireUser(email);
        assertIsMember(user, workspaceId);
        Task task = taskRepository.findByIdAndWorkspaceId(taskId, workspaceId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        String content = request.getContent().trim();
        if (content.isEmpty()) {
            throw new RuntimeException("Nội dung bình luận không được để trống");
        }

        TaskComment saved = taskCommentRepository.save(TaskComment.builder()
                .task(task)
                .user(user)
                .content(content)
                .build());
        return mapComment(saved);
    }

    @Override
    @Transactional
    public TaskAttachmentResponse addAttachment(
            String email, Long workspaceId, Long taskId, MultipartFile file) {
        User user = requireUser(email);
        assertIsMember(user, workspaceId);
        Task task = taskRepository.findByIdAndWorkspaceId(taskId, workspaceId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        if (file == null || file.isEmpty()) {
            throw new RuntimeException("File không hợp lệ");
        }

        String url = fileService.uploadFile(file);
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String contentType = file.getContentType();

        TaskAttachment attachment = taskAttachmentRepository.save(TaskAttachment.builder()
                .task(task)
                .fileName(originalName)
                .fileUrl(url)
                .fileType(contentType)
                .uploadedBy(user)
                .build());

        return mapAttachment(attachment);
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
    }

    private Workspace getWorkspaceOrThrow(Long workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new RuntimeException("Workspace not found"));
    }

    private void assertIsMember(User user, Long workspaceId) {
        Workspace workspace = getWorkspaceOrThrow(workspaceId);
        if (workspace.getOwner().getId().equals(user.getId())) {
            return;
        }
        workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, user.getId())
                .orElseThrow(() -> new RuntimeException("You are not a member of this workspace"));
    }

    private void assertTaskInWorkspace(Long taskId, Long workspaceId) {
        taskRepository.findByIdAndWorkspaceId(taskId, workspaceId)
                .orElseThrow(() -> new RuntimeException("Task not found"));
    }

    private int nextPosition(Long workspaceId, TaskStatus status) {
        List<Task> existing = taskRepository.findByWorkspaceIdAndStatusOrderByPositionAscIdAsc(workspaceId, status);
        if (existing.isEmpty()) {
            return 0;
        }
        return existing.get(existing.size() - 1).getPosition() + 1;
    }

    private Set<User> resolveAssignees(Long workspaceId, List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new HashSet<>();
        }
        Workspace workspace = getWorkspaceOrThrow(workspaceId);
        Set<User> assignees = new HashSet<>();
        for (Long userId : userIds) {
            User assignee = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("Assignee not found: " + userId));
            boolean isOwner = workspace.getOwner().getId().equals(userId);
            boolean isMember = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId).isPresent();
            if (!isOwner && !isMember) {
                throw new RuntimeException("User is not a workspace member: " + userId);
            }
            assignees.add(assignee);
        }
        return assignees;
    }

    private TaskResponse mapToResponse(Task task) {
        List<TaskAssigneeResponse> assignees = task.getAssignees().stream()
                .map(u -> TaskAssigneeResponse.builder()
                        .userId(u.getId())
                        .userName(u.getFullName())
                        .email(u.getEmail())
                        .build())
                .collect(Collectors.toList());
        List<TaskAttachmentResponse> attachments = taskAttachmentRepository
                .findByTaskIdOrderByUploadedAtDesc(task.getId()).stream()
                .map(this::mapAttachment)
                .collect(Collectors.toList());
        long commentCount = taskCommentRepository.findByTaskIdOrderByCreatedAtAsc(task.getId()).size();

        return TaskResponse.builder()
                .id(task.getId())
                .workspaceId(task.getWorkspace().getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus())
                .dueDate(task.getDueDate())
                .position(task.getPosition())
                .assignees(assignees)
                .attachments(attachments)
                .commentCount((int) commentCount)
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private TaskCommentResponse mapComment(TaskComment comment) {
        return TaskCommentResponse.builder()
                .id(comment.getId())
                .taskId(comment.getTask().getId())
                .userId(comment.getUser().getId())
                .userName(comment.getUser().getFullName())
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .build();
    }

    private TaskAttachmentResponse mapAttachment(TaskAttachment attachment) {
        return TaskAttachmentResponse.builder()
                .id(attachment.getId())
                .fileName(attachment.getFileName())
                .fileUrl(attachment.getFileUrl())
                .fileType(attachment.getFileType())
                .uploadedBy(attachment.getUploadedBy().getId())
                .uploadedByName(attachment.getUploadedBy().getFullName())
                .uploadedAt(attachment.getUploadedAt())
                .build();
    }
}
