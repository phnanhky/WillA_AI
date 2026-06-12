package com.willa.ai.backend.service.impl;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.willa.ai.backend.dto.request.TaskChecklistItemRequest;
import com.willa.ai.backend.dto.request.TaskChecklistRequest;
import com.willa.ai.backend.dto.request.TaskCommentRequest;
import com.willa.ai.backend.dto.request.TaskRequest;
import com.willa.ai.backend.dto.response.TaskAssigneeResponse;
import com.willa.ai.backend.dto.response.TaskAttachmentResponse;
import com.willa.ai.backend.dto.response.TaskChecklistItemResponse;
import com.willa.ai.backend.dto.response.TaskChecklistResponse;
import com.willa.ai.backend.dto.response.TaskCommentResponse;
import com.willa.ai.backend.dto.response.TaskResponse;
import com.willa.ai.backend.entity.Task;
import com.willa.ai.backend.entity.TaskAttachment;
import com.willa.ai.backend.entity.TaskChecklist;
import com.willa.ai.backend.entity.TaskChecklistItem;
import com.willa.ai.backend.entity.TaskComment;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.Workspace;
import com.willa.ai.backend.entity.enums.ChecklistPriority;
import com.willa.ai.backend.entity.enums.TaskStatus;
import com.willa.ai.backend.repository.TaskAttachmentRepository;
import com.willa.ai.backend.repository.TaskChecklistItemRepository;
import com.willa.ai.backend.repository.TaskChecklistRepository;
import com.willa.ai.backend.repository.TaskCommentRepository;
import com.willa.ai.backend.repository.TaskRepository;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.repository.WorkspaceMemberRepository;
import com.willa.ai.backend.repository.WorkspaceRepository;
import com.willa.ai.backend.service.FileService;
import com.willa.ai.backend.service.GoogleMeetService;
import com.willa.ai.backend.service.TaskService;
import com.willa.ai.backend.service.WorkspaceRealtimeService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final TaskCommentRepository taskCommentRepository;
    private final TaskAttachmentRepository taskAttachmentRepository;
    private final TaskChecklistRepository taskChecklistRepository;
    private final TaskChecklistItemRepository taskChecklistItemRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final FileService fileService;
    private final GoogleMeetService googleMeetService;
    private final WorkspaceRealtimeService workspaceRealtimeService;

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

        TaskResponse response = mapToResponse(taskRepository.save(task));
        notifyWorkspaceChanged(workspaceId);
        return response;
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
        if (request.getMeetLink() != null) {
            task.setMeetLink(request.getMeetLink().isBlank() ? null : request.getMeetLink().trim());
        }
        if (Boolean.TRUE.equals(request.getClearLabelPriority())) {
            task.setLabelPriority(ChecklistPriority.NONE);
        } else if (request.getLabelPriority() != null) {
            task.setLabelPriority(request.getLabelPriority());
        }
        if (request.getCompleted() != null) {
            task.setCompleted(request.getCompleted());
            if (Boolean.TRUE.equals(request.getCompleted())) {
                task.setCompletedAt(LocalDateTime.now());
            } else {
                task.setCompletedAt(null);
            }
        }

        TaskResponse response = mapToResponse(taskRepository.save(task));
        notifyWorkspaceChanged(workspaceId);
        return response;
    }

    @Override
    @Transactional
    public TaskResponse createGoogleMeet(String email, Long workspaceId, Long taskId) {
        User user = requireUser(email);
        assertIsMember(user, workspaceId);
        Task task = taskRepository.findByIdAndWorkspaceId(taskId, workspaceId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        String link = googleMeetService.createMeetLink(
                "Willa: " + task.getTitle(),
                task.getDescription());
        task.setMeetLink(link);
        TaskResponse response = mapToResponse(taskRepository.save(task));
        notifyWorkspaceChanged(workspaceId);
        return response;
    }

    @Override
    @Transactional
    public TaskResponse removeGoogleMeet(String email, Long workspaceId, Long taskId) {
        User user = requireUser(email);
        assertIsMember(user, workspaceId);
        Task task = taskRepository.findByIdAndWorkspaceId(taskId, workspaceId)
                .orElseThrow(() -> new RuntimeException("Task not found"));
        task.setMeetLink(null);
        TaskResponse response = mapToResponse(taskRepository.save(task));
        notifyWorkspaceChanged(workspaceId);
        return response;
    }

    @Override
    @Transactional
    public void deleteTask(String email, Long workspaceId, Long taskId) {
        User user = requireUser(email);
        assertIsMember(user, workspaceId);
        Task task = taskRepository.findByIdAndWorkspaceId(taskId, workspaceId)
                .orElseThrow(() -> new RuntimeException("Task not found"));
        taskRepository.delete(task);
        notifyWorkspaceChanged(workspaceId);
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
        notifyWorkspaceChanged(workspaceId);
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

        notifyWorkspaceChanged(workspaceId);
        return mapAttachment(attachment);
    }

    @Override
    @Transactional
    public TaskAttachmentResponse updateAttachment(
            String email, Long workspaceId, Long taskId, Long attachmentId, String fileName) {
        User user = requireUser(email);
        assertIsMember(user, workspaceId);
        assertTaskInWorkspace(taskId, workspaceId);
        TaskAttachment attachment = taskAttachmentRepository.findByIdAndTaskId(attachmentId, taskId)
                .orElseThrow(() -> new RuntimeException("Attachment not found"));
        String trimmed = fileName != null ? fileName.trim() : "";
        if (trimmed.isEmpty()) {
            throw new RuntimeException("Tên tệp không được để trống");
        }
        attachment.setFileName(trimmed);
        TaskAttachmentResponse response = mapAttachment(taskAttachmentRepository.save(attachment));
        notifyWorkspaceChanged(workspaceId);
        return response;
    }

    @Override
    @Transactional
    public void deleteAttachment(String email, Long workspaceId, Long taskId, Long attachmentId) {
        User user = requireUser(email);
        assertIsMember(user, workspaceId);
        assertTaskInWorkspace(taskId, workspaceId);
        TaskAttachment attachment = taskAttachmentRepository.findByIdAndTaskId(attachmentId, taskId)
                .orElseThrow(() -> new RuntimeException("Attachment not found"));
        taskAttachmentRepository.delete(attachment);
        notifyWorkspaceChanged(workspaceId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskChecklistResponse> listChecklists(String email, Long workspaceId, Long taskId) {
        User user = requireUser(email);
        assertIsMember(user, workspaceId);
        assertTaskInWorkspace(taskId, workspaceId);
        return taskChecklistRepository.findByTaskIdOrderByPositionAscIdAsc(taskId).stream()
                .map(this::mapChecklist)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public TaskChecklistResponse createChecklist(String email, Long workspaceId, Long taskId, TaskChecklistRequest request) {
        User user = requireUser(email);
        assertIsMember(user, workspaceId);
        Task task = taskRepository.findByIdAndWorkspaceId(taskId, workspaceId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        String title = request.getTitle().trim();
        if (title.isEmpty()) {
            throw new RuntimeException("Tiêu đề checklist không được để trống");
        }

        List<TaskChecklist> existing = taskChecklistRepository.findByTaskIdOrderByPositionAscIdAsc(taskId);
        int position = existing.isEmpty() ? 0 : existing.get(existing.size() - 1).getPosition() + 1;

        TaskChecklist saved = taskChecklistRepository.save(TaskChecklist.builder()
                .task(task)
                .title(title)
                .position(position)
                .build());
        TaskChecklistResponse response = mapChecklist(saved);
        notifyWorkspaceChanged(workspaceId);
        return response;
    }

    @Override
    @Transactional
    public TaskChecklistResponse updateChecklist(
            String email, Long workspaceId, Long taskId, Long checklistId, TaskChecklistRequest request) {
        User user = requireUser(email);
        assertIsMember(user, workspaceId);
        TaskChecklist checklist = getChecklistOrThrow(taskId, checklistId);

        String title = request.getTitle().trim();
        if (title.isEmpty()) {
            throw new RuntimeException("Tiêu đề checklist không được để trống");
        }
        checklist.setTitle(title);
        TaskChecklistResponse response = mapChecklist(taskChecklistRepository.save(checklist));
        notifyWorkspaceChanged(workspaceId);
        return response;
    }

    @Override
    @Transactional
    public void deleteChecklist(String email, Long workspaceId, Long taskId, Long checklistId) {
        User user = requireUser(email);
        assertIsMember(user, workspaceId);
        TaskChecklist checklist = getChecklistOrThrow(taskId, checklistId);
        taskChecklistRepository.delete(checklist);
        notifyWorkspaceChanged(workspaceId);
    }

    @Override
    @Transactional
    public TaskChecklistItemResponse addChecklistItem(
            String email, Long workspaceId, Long taskId, Long checklistId, TaskChecklistItemRequest request) {
        User user = requireUser(email);
        assertIsMember(user, workspaceId);
        TaskChecklist checklist = getChecklistOrThrow(taskId, checklistId);

        String title = request.getTitle() != null ? request.getTitle().trim() : "";
        if (title.isEmpty()) {
            throw new RuntimeException("Nội dung mục không được để trống");
        }

        List<TaskChecklistItem> existing = taskChecklistItemRepository.findByChecklistIdOrderByPositionAscIdAsc(checklistId);
        int position = existing.isEmpty() ? 0 : existing.get(existing.size() - 1).getPosition() + 1;

        TaskChecklistItem item = TaskChecklistItem.builder()
                .checklist(checklist)
                .title(title)
                .completed(false)
                .position(position)
                .dueDate(request.getDueDate())
                .assignee(resolveOptionalAssignee(workspaceId, request.getAssigneeUserId()))
                .priority(request.getPriority() != null ? request.getPriority() : ChecklistPriority.NONE)
                .build();

        TaskChecklistItemResponse response = mapChecklistItem(taskChecklistItemRepository.save(item));
        notifyWorkspaceChanged(workspaceId);
        return response;
    }

    @Override
    @Transactional
    public TaskChecklistItemResponse updateChecklistItem(
            String email,
            Long workspaceId,
            Long taskId,
            Long checklistId,
            Long itemId,
            TaskChecklistItemRequest request) {
        User user = requireUser(email);
        assertIsMember(user, workspaceId);
        getChecklistOrThrow(taskId, checklistId);
        TaskChecklistItem item = taskChecklistItemRepository.findByIdAndChecklistId(itemId, checklistId)
                .orElseThrow(() -> new RuntimeException("Checklist item not found"));

        if (request.getTitle() != null) {
            String title = request.getTitle().trim();
            if (title.isEmpty()) {
                throw new RuntimeException("Nội dung mục không được để trống");
            }
            item.setTitle(title);
        }
        if (request.getCompleted() != null) {
            item.setCompleted(request.getCompleted());
            if (Boolean.TRUE.equals(request.getCompleted())) {
                item.setCompletedAt(LocalDateTime.now());
            } else {
                item.setCompletedAt(null);
            }
        }
        if (Boolean.TRUE.equals(request.getClearDueDate())) {
            item.setDueDate(null);
        } else if (request.getDueDate() != null) {
            item.setDueDate(request.getDueDate());
        }
        if (Boolean.TRUE.equals(request.getClearAssignee())) {
            item.setAssignee(null);
        } else if (request.getAssigneeUserId() != null) {
            item.setAssignee(resolveOptionalAssignee(workspaceId, request.getAssigneeUserId()));
        }
        if (request.getPriority() != null) {
            item.setPriority(request.getPriority());
        }

        TaskChecklistItemResponse response = mapChecklistItem(taskChecklistItemRepository.save(item));
        notifyWorkspaceChanged(workspaceId);
        return response;
    }

    @Override
    @Transactional
    public void deleteChecklistItem(
            String email, Long workspaceId, Long taskId, Long checklistId, Long itemId) {
        User user = requireUser(email);
        assertIsMember(user, workspaceId);
        getChecklistOrThrow(taskId, checklistId);
        TaskChecklistItem item = taskChecklistItemRepository.findByIdAndChecklistId(itemId, checklistId)
                .orElseThrow(() -> new RuntimeException("Checklist item not found"));
        taskChecklistItemRepository.delete(item);
        notifyWorkspaceChanged(workspaceId);
    }

    private TaskChecklist getChecklistOrThrow(Long taskId, Long checklistId) {
        return taskChecklistRepository.findByIdAndTaskId(checklistId, taskId)
                .orElseThrow(() -> new RuntimeException("Checklist not found"));
    }

    private User resolveOptionalAssignee(Long workspaceId, Long userId) {
        if (userId == null) {
            return null;
        }
        Workspace workspace = getWorkspaceOrThrow(workspaceId);
        User assignee = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Assignee not found: " + userId));
        boolean isOwner = workspace.getOwner().getId().equals(userId);
        boolean isMember = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId).isPresent();
        if (!isOwner && !isMember) {
            throw new RuntimeException("User is not a workspace member: " + userId);
        }
        return assignee;
    }

    private TaskChecklistResponse mapChecklist(TaskChecklist checklist) {
        List<TaskChecklistItemResponse> items = taskChecklistItemRepository
                .findByChecklistIdOrderByPositionAscIdAsc(checklist.getId()).stream()
                .map(this::mapChecklistItem)
                .collect(Collectors.toList());
        return TaskChecklistResponse.builder()
                .id(checklist.getId())
                .taskId(checklist.getTask().getId())
                .title(checklist.getTitle())
                .position(checklist.getPosition())
                .items(items)
                .build();
    }

    private TaskChecklistItemResponse mapChecklistItem(TaskChecklistItem item) {
        User assignee = item.getAssignee();
        return TaskChecklistItemResponse.builder()
                .id(item.getId())
                .checklistId(item.getChecklist().getId())
                .title(item.getTitle())
                .completed(item.getCompleted())
                .completedAt(item.getCompletedAt())
                .position(item.getPosition())
                .dueDate(item.getDueDate())
                .assigneeUserId(assignee != null ? assignee.getId() : null)
                .assigneeName(assignee != null ? assignee.getFullName() : null)
                .priority(item.getPriority())
                .build();
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
                .meetLink(task.getMeetLink())
                .labelPriority(task.getLabelPriority())
                .completed(task.getCompleted())
                .completedAt(task.getCompletedAt())
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

    private void notifyWorkspaceChanged(Long workspaceId) {
        workspaceRealtimeService.publishWorkspaceChanged(workspaceId);
    }
}
