package com.willa.ai.backend.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.willa.ai.backend.dto.request.WorkspaceProjectRequest;
import com.willa.ai.backend.dto.response.WorkspaceProjectResponse;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.Workspace;
import com.willa.ai.backend.entity.WorkspaceChannel;
import com.willa.ai.backend.entity.WorkspaceProject;
import com.willa.ai.backend.repository.TaskRepository;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.repository.WorkspaceChannelRepository;
import com.willa.ai.backend.repository.WorkspaceMemberRepository;
import com.willa.ai.backend.repository.WorkspaceProjectRepository;
import com.willa.ai.backend.repository.WorkspaceRepository;
import com.willa.ai.backend.service.WorkspaceChannelService;
import com.willa.ai.backend.service.WorkspaceDataPurger;
import com.willa.ai.backend.service.WorkspaceProjectService;
import com.willa.ai.backend.service.WorkspaceRealtimeService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceProjectServiceImpl implements WorkspaceProjectService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceProjectRepository projectRepository;
    private final WorkspaceChannelRepository channelRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final WorkspaceChannelService workspaceChannelService;
    private final WorkspaceDataPurger workspaceDataPurger;
    private final WorkspaceRealtimeService workspaceRealtimeService;

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceProjectResponse> listProjects(String email, Long workspaceId) {
        User user = requireUser(email);
        assertIsMember(user, workspaceId);
        return projectRepository.findByWorkspaceIdOrderByPositionAscIdAsc(workspaceId).stream()
                .map(this::mapProject)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public WorkspaceProjectResponse createProject(String email, Long workspaceId, WorkspaceProjectRequest request) {
        User user = requireUser(email);
        assertIsMember(user, workspaceId);
        Workspace workspace = getWorkspaceOrThrow(workspaceId);
        String name = normalizeName(request.getName());
        if (projectRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, name)) {
            throw new RuntimeException("Project \"" + name + "\" đã tồn tại");
        }
        int nextPos = projectRepository.findByWorkspaceIdOrderByPositionAscIdAsc(workspaceId).size();
        WorkspaceProject project = projectRepository.save(WorkspaceProject.builder()
                .workspace(workspace)
                .name(name)
                .position(nextPos)
                .build());
        WorkspaceChannel channel = workspaceChannelService.ensureProjectChannel(workspace, name);
        WorkspaceProjectResponse response = mapProject(project, channel.getId(), 0);
        workspaceRealtimeService.publishChannelChanged(workspaceId, "CHANNEL_CREATED", channel.getId());
        workspaceRealtimeService.publishWorkspaceChanged(workspaceId);
        return response;
    }

    @Override
    @Transactional
    public void deleteProject(String email, Long workspaceId, Long projectId) {
        User user = requireUser(email);
        assertIsMember(user, workspaceId);
        WorkspaceProject project = projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> new RuntimeException("Project không tồn tại"));
        List<Long> taskIds = taskRepository.findByWorkspaceIdAndProjectIdOrderByPositionAscIdAsc(workspaceId, projectId)
                .stream()
                .map(t -> t.getId())
                .collect(Collectors.toList());
        for (Long taskId : taskIds) {
            workspaceDataPurger.purgeTaskData(taskId);
            taskRepository.deleteById(taskId);
        }
        channelRepository.findByWorkspaceIdAndNameIgnoreCase(workspaceId, project.getName())
                .filter(ch -> !Boolean.TRUE.equals(ch.getIsSystem()))
                .ifPresent(ch -> {
                    channelRepository.delete(ch);
                    workspaceRealtimeService.publishChannelChanged(workspaceId, "CHANNEL_DELETED", ch.getId());
                });
        projectRepository.delete(project);
        workspaceRealtimeService.publishWorkspaceChanged(workspaceId);
    }

    private String normalizeName(String raw) {
        String name = raw.trim();
        if (name.isEmpty()) {
            throw new RuntimeException("Tên project không hợp lệ");
        }
        if (name.length() > 120) {
            throw new RuntimeException("Tên project quá dài (tối đa 120 ký tự)");
        }
        return name;
    }

    private WorkspaceProjectResponse mapProject(WorkspaceProject project) {
        Long channelId = channelRepository.findByWorkspaceIdAndNameIgnoreCase(
                project.getWorkspace().getId(), project.getName())
                .map(WorkspaceChannel::getId)
                .orElse(null);
        int taskCount = (int) taskRepository.countByWorkspaceIdAndProjectId(
                project.getWorkspace().getId(), project.getId());
        return mapProject(project, channelId, taskCount);
    }

    private WorkspaceProjectResponse mapProject(WorkspaceProject project, Long channelId, int taskCount) {
        return WorkspaceProjectResponse.builder()
                .id(project.getId())
                .workspaceId(project.getWorkspace().getId())
                .name(project.getName())
                .position(project.getPosition())
                .channelId(channelId)
                .taskCount(taskCount)
                .createdAt(project.getCreatedAt())
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
}
