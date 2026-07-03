package com.willa.ai.backend.service.impl;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.willa.ai.backend.dto.response.MemberPerformanceResponse;
import com.willa.ai.backend.dto.response.WorkspaceMemberStatsResponse;
import com.willa.ai.backend.entity.Task;
import com.willa.ai.backend.entity.TaskChecklistItem;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.Workspace;
import com.willa.ai.backend.entity.WorkspaceMember;
import com.willa.ai.backend.entity.enums.WorkspaceRole;
import com.willa.ai.backend.repository.TaskChecklistItemRepository;
import com.willa.ai.backend.repository.TaskRepository;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.repository.WorkspaceMemberRepository;
import com.willa.ai.backend.repository.WorkspaceRepository;
import com.willa.ai.backend.service.WorkspaceMemberStatsService;

import lombok.RequiredArgsConstructor;

/**
 * Thống kê hiệu suất:
 * - Task có ít nhất một mục checklist → chỉ tính theo mục checklist (có assignee), bỏ qua task.
 * - Task không có mục checklist → tính theo assignee của task.
 * - Đúng/trễ hạn so sánh completedAt với dueDate của mục/task tương ứng.
 */
@Service
@RequiredArgsConstructor
public class WorkspaceMemberStatsServiceImpl implements WorkspaceMemberStatsService {

    private final TaskRepository taskRepository;
    private final TaskChecklistItemRepository taskChecklistItemRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public WorkspaceMemberStatsResponse getMemberStats(String email, Long workspaceId) {
        User viewer = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new RuntimeException("Workspace not found"));
        assertIsMember(viewer, workspace);

        boolean ownerView = workspace.getOwner().getId().equals(viewer.getId());
        Map<Long, StatsAccumulator> statsByUser = new LinkedHashMap<>();

        User owner = workspace.getOwner();
        statsByUser.put(owner.getId(), new StatsAccumulator(owner, WorkspaceRole.OWNER));

        for (WorkspaceMember member : workspaceMemberRepository.findByWorkspaceId(workspaceId)) {
            if (member.getUser().getId().equals(owner.getId())) {
                continue;
            }
            statsByUser.put(
                    member.getUser().getId(),
                    new StatsAccumulator(member.getUser(), member.getRole()));
        }

        LocalDateTime now = LocalDateTime.now();
        Set<Long> taskIdsWithChecklistItems =
                taskChecklistItemRepository.findTaskIdsWithChecklistItemsByWorkspaceId(workspaceId);

        List<Task> tasks = taskRepository.findByWorkspaceIdWithAssignees(workspaceId);
        for (Task task : tasks) {
            if (taskIdsWithChecklistItems.contains(task.getId())) {
                continue;
            }
            for (User assignee : task.getAssignees()) {
                StatsAccumulator acc = statsByUser.get(assignee.getId());
                if (acc == null) {
                    continue;
                }
                acc.addWorkUnit(
                        Boolean.TRUE.equals(task.getCompleted()),
                        task.getCompletedAt(),
                        task.getDueDate(),
                        now);
            }
        }

        List<TaskChecklistItem> checklistItems = taskChecklistItemRepository.findByWorkspaceId(workspaceId);
        for (TaskChecklistItem item : checklistItems) {
            Set<User> itemAssignees = item.getAssignees();
            if (itemAssignees == null || itemAssignees.isEmpty()) {
                if (item.getAssignee() == null) {
                    continue;
                }
                itemAssignees = Set.of(item.getAssignee());
            }
            for (User assignee : itemAssignees) {
                StatsAccumulator acc = statsByUser.get(assignee.getId());
                if (acc == null) {
                    continue;
                }
                acc.addWorkUnit(
                        Boolean.TRUE.equals(item.getCompleted()),
                        item.getCompletedAt(),
                        item.getDueDate(),
                        now);
            }
        }

        List<MemberPerformanceResponse> members = statsByUser.values().stream()
                .map(StatsAccumulator::toResponse)
                .collect(Collectors.toList());

        if (!ownerView) {
            members = members.stream()
                    .filter(m -> m.getUserId().equals(viewer.getId()))
                    .collect(Collectors.toList());
        }

        return WorkspaceMemberStatsResponse.builder()
                .workspaceId(workspaceId)
                .viewerUserId(viewer.getId())
                .ownerView(ownerView)
                .members(members)
                .build();
    }

    private void assertIsMember(User user, Workspace workspace) {
        if (workspace.getOwner().getId().equals(user.getId())) {
            return;
        }
        workspaceMemberRepository.findByWorkspaceIdAndUserId(workspace.getId(), user.getId())
                .orElseThrow(() -> new RuntimeException("You are not a member of this workspace"));
    }

    private static final class StatsAccumulator {
        private final User user;
        private final WorkspaceRole role;
        private int totalAssigned;
        private int completedCount;
        private int dueTotal;
        private int onTimeCount;
        private int lateCount;

        StatsAccumulator(User user, WorkspaceRole role) {
            this.user = user;
            this.role = role;
        }

        void addWorkUnit(
                boolean completed,
                LocalDateTime completedAt,
                LocalDateTime dueDate,
                LocalDateTime now) {
            totalAssigned++;
            if (completed) {
                completedCount++;
            }
            if (dueDate != null) {
                dueTotal++;
                if (isLate(dueDate, completed, completedAt, now)) {
                    lateCount++;
                } else {
                    onTimeCount++;
                }
            }
        }

        MemberPerformanceResponse toResponse() {
            double completionRate = totalAssigned == 0
                    ? 0.0
                    : round2(completedCount * 100.0 / totalAssigned);
            double onTimeRate = dueTotal == 0 ? 0.0 : round2(onTimeCount * 100.0 / dueTotal);
            double lateRate = dueTotal == 0 ? 0.0 : round2(lateCount * 100.0 / dueTotal);

            return MemberPerformanceResponse.builder()
                    .userId(user.getId())
                    .userName(user.getFullName())
                    .email(user.getEmail())
                    .role(role)
                    .totalAssigned(totalAssigned)
                    .completedCount(completedCount)
                    .completionRate(completionRate)
                    .dueTotal(dueTotal)
                    .onTimeCount(onTimeCount)
                    .lateCount(lateCount)
                    .onTimeRate(onTimeRate)
                    .lateRate(lateRate)
                    .build();
        }
    }

    /** Hoàn thành sau hết ngày hạn hoặc chưa xong mà đã quá hạn (cuối ngày due). */
    private static boolean isLate(
            LocalDateTime dueDate,
            boolean completed,
            LocalDateTime completedAt,
            LocalDateTime now) {
        LocalDateTime dueEnd = dueDate.toLocalDate().atTime(23, 59, 59);
        if (completed) {
            LocalDateTime finishedAt = completedAt != null ? completedAt : now;
            return finishedAt.isAfter(dueEnd);
        }
        return now.isAfter(dueEnd);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
