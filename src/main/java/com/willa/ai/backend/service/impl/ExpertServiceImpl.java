package com.willa.ai.backend.service.impl;

import com.willa.ai.backend.dto.request.WorkspaceExpertRequest;
import com.willa.ai.backend.dto.response.AdminWorkspaceSummaryResponse;
import com.willa.ai.backend.dto.response.WorkspaceExpertResponse;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.Workspace;
import com.willa.ai.backend.entity.WorkspaceExpert;
import com.willa.ai.backend.entity.WorkspaceMember;
import com.willa.ai.backend.entity.enums.WorkspaceRole;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.repository.WorkspaceExpertRepository;
import com.willa.ai.backend.repository.WorkspaceMemberRepository;
import com.willa.ai.backend.repository.WorkspaceRepository;
import com.willa.ai.backend.service.ExpertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ExpertServiceImpl implements ExpertService {

    private final WorkspaceExpertRepository expertRepository;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceExpertResponse> listAllExperts() {
        return expertRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceExpertResponse> listPlatformExperts() {
        return expertRepository.findByWorkspaceIsNullAndIsActiveTrueOrderByCreatedAtDesc().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceExpertResponse> listWorkspaceExperts(String currentEmail, Long workspaceId) {
        User currentUser = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        assertWorkspaceMember(workspaceId, currentUser.getId());

        Map<Long, WorkspaceExpertResponse> merged = new LinkedHashMap<>();
        for (WorkspaceExpert expert : expertRepository.findByWorkspaceIsNullAndIsActiveTrueOrderByCreatedAtDesc()) {
            merged.put(expert.getId(), mapToResponse(expert));
        }
        for (WorkspaceExpert expert : expertRepository.findByWorkspaceIdAndIsActiveTrueOrderByCreatedAtDesc(workspaceId)) {
            merged.put(expert.getId(), mapToResponse(expert));
        }
        return new ArrayList<>(merged.values());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminWorkspaceSummaryResponse> listAllWorkspacesForAdmin() {
        return workspaceRepository.findAll().stream()
                .sorted(Comparator.comparing(Workspace::getTitle, String.CASE_INSENSITIVE_ORDER))
                .map(w -> AdminWorkspaceSummaryResponse.builder()
                        .id(w.getId())
                        .title(w.getTitle())
                        .ownerName(w.getOwner() != null ? w.getOwner().getFullName() : null)
                        .ownerEmail(w.getOwner() != null ? w.getOwner().getEmail() : null)
                        .build())
                .toList();
    }

    @Override
    public WorkspaceExpertResponse createExpert(WorkspaceExpertRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng với email: " + email));

        Long workspaceId = request.getWorkspaceId();
        if (workspaceId == null) {
            if (expertRepository.existsByWorkspaceIsNullAndUserId(user.getId())) {
                throw new RuntimeException("Người dùng đã có phạm vi Platform. Có thể thêm workspace khác bằng cùng email.");
            }
            WorkspaceExpert expert = expertRepository.save(WorkspaceExpert.builder()
                    .workspace(null)
                    .user(user)
                    .expertise(trimOrNull(request.getExpertise()))
                    .bio(trimOrNull(request.getBio()))
                    .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                    .reviewPrice(normalizePrice(request.getReviewPrice()))
                    .hourlyRate(normalizePrice(request.getHourlyRate()))
                    .build());
            log.info("Admin assigned platform expert userId={}", user.getId());
            return mapToResponse(expert);
        }

        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy workspace"));

        if (expertRepository.existsByWorkspaceIdAndUserId(workspace.getId(), user.getId())) {
            throw new RuntimeException("Người dùng đã là expert trong workspace này. Chọn workspace khác hoặc Platform.");
        }

        ensureWorkspaceMember(workspace, user);

        WorkspaceExpert expert = expertRepository.save(WorkspaceExpert.builder()
                .workspace(workspace)
                .user(user)
                .expertise(trimOrNull(request.getExpertise()))
                .bio(trimOrNull(request.getBio()))
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build());

        log.info("Admin assigned expert userId={} to workspaceId={}", user.getId(), workspace.getId());
        return mapToResponse(expert);
    }

    @Override
    public WorkspaceExpertResponse updateExpert(Long expertId, WorkspaceExpertRequest request) {
        WorkspaceExpert expert = expertRepository.findById(expertId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy expert"));

        if (request.getExpertise() != null) {
            expert.setExpertise(trimOrNull(request.getExpertise()));
        }
        if (request.getBio() != null) {
            expert.setBio(trimOrNull(request.getBio()));
        }
        if (request.getIsActive() != null) {
            expert.setIsActive(request.getIsActive());
        }
        if (request.getReviewPrice() != null) {
            expert.setReviewPrice(normalizePrice(request.getReviewPrice()));
        }
        if (request.getHourlyRate() != null) {
            expert.setHourlyRate(normalizePrice(request.getHourlyRate()));
        }

        return mapToResponse(expertRepository.save(expert));
    }

    @Override
    public void deleteExpert(Long expertId) {
        if (!expertRepository.existsById(expertId)) {
            throw new RuntimeException("Không tìm thấy expert");
        }
        expertRepository.deleteById(expertId);
    }

    private void ensureWorkspaceMember(Workspace workspace, User user) {
        if (workspaceMemberRepository.findByWorkspaceIdAndUserId(workspace.getId(), user.getId()).isPresent()) {
            return;
        }
        workspaceMemberRepository.save(WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(WorkspaceRole.MEMBER)
                .build());
        log.info("Auto-added userId={} as workspace member for expert assignment", user.getId());
    }

    private void assertWorkspaceMember(Long workspaceId, Long userId) {
        workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new RuntimeException("You are not a member of this workspace"));
    }

    private String trimOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Long normalizePrice(Long value) {
        return value != null && value > 0 ? value : null;
    }

    private WorkspaceExpertResponse mapToResponse(WorkspaceExpert expert) {
        User user = expert.getUser();
        Workspace workspace = expert.getWorkspace();
        boolean platform = workspace == null;
        return WorkspaceExpertResponse.builder()
                .id(expert.getId())
                .userId(user.getId())
                .userEmail(user.getEmail())
                .userFullName(user.getFullName())
                .userAvatarUrl(user.getAvatarUrl())
                .workspaceId(platform ? null : workspace.getId())
                .workspaceTitle(platform ? null : workspace.getTitle())
                .platformExpert(platform)
                .expertise(expert.getExpertise())
                .bio(expert.getBio())
                .isActive(expert.getIsActive())
                .reviewPrice(expert.getReviewPrice())
                .hourlyRate(expert.getHourlyRate())
                .createdAt(expert.getCreatedAt())
                .build();
    }
}
