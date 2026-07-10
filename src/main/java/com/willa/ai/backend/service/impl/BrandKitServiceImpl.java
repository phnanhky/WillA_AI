package com.willa.ai.backend.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.willa.ai.backend.client.AiServerClient;
import com.willa.ai.backend.dto.request.CreateBrandKitProfileRequest;
import com.willa.ai.backend.dto.request.UpdateBrandKitProfileRequest;
import com.willa.ai.backend.dto.response.BrandKitCheckAssetResponse;
import com.willa.ai.backend.dto.response.BrandKitCheckDetailResponse;
import com.willa.ai.backend.dto.response.BrandKitCheckSummaryResponse;
import com.willa.ai.backend.dto.response.BrandKitProfileResponse;
import com.willa.ai.backend.dto.response.BrandKitReferenceImageResponse;
import com.willa.ai.backend.entity.BrandKitCheck;
import com.willa.ai.backend.entity.BrandKitCheckAsset;
import com.willa.ai.backend.entity.BrandKitProfile;
import com.willa.ai.backend.entity.BrandKitReferenceImage;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.Workspace;
import com.willa.ai.backend.entity.enums.BrandKitCheckStatus;
import com.willa.ai.backend.entity.enums.WorkflowType;
import com.willa.ai.backend.repository.BrandKitCheckAssetRepository;
import com.willa.ai.backend.repository.BrandKitCheckRepository;
import com.willa.ai.backend.repository.BrandKitProfileRepository;
import com.willa.ai.backend.repository.BrandKitReferenceImageRepository;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.repository.WorkspaceRepository;
import com.willa.ai.backend.service.BrandKitService;
import com.willa.ai.backend.service.FileService;
import com.willa.ai.backend.service.WorkflowUsageService;
import com.willa.ai.backend.util.UploadSizeValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BrandKitServiceImpl implements BrandKitService {

    private static final int MAX_REF_IMAGES = 3;
    private static final int MAX_CHECK_IMAGES = 20;

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final BrandKitProfileRepository profileRepository;
    private final BrandKitReferenceImageRepository referenceImageRepository;
    private final BrandKitCheckRepository checkRepository;
    private final BrandKitCheckAssetRepository checkAssetRepository;
    private final FileService fileService;
    private final AiServerClient aiServerClient;
    private final WorkflowUsageService workflowUsageService;
    private final UploadSizeValidator uploadSizeValidator;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<BrandKitProfileResponse> listProfiles(String email) {
        User user = requireUser(email);
        return profileRepository.findByUserIdOrderByUpdatedAtDesc(user.getId()).stream()
                .map(this::mapProfile)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public BrandKitProfileResponse getProfile(String email, Long profileId) {
        User user = requireUser(email);
        BrandKitProfile profile = requireProfile(user.getId(), profileId);
        return mapProfile(profile);
    }

    @Override
    @Transactional
    public BrandKitProfileResponse createProfile(String email, CreateBrandKitProfileRequest request) {
        User user = requireUser(email);
        Workspace workspace = resolveWorkspace(user, request.getWorkspaceId());
        BrandKitProfile profile = profileRepository.save(BrandKitProfile.builder()
                .user(user)
                .workspace(workspace)
                .title(request.getTitle().trim())
                .build());
        return mapProfile(profile);
    }

    @Override
    @Transactional
    public BrandKitProfileResponse updateProfile(String email, Long profileId, UpdateBrandKitProfileRequest request) {
        User user = requireUser(email);
        BrandKitProfile profile = requireProfile(user.getId(), profileId);
        profile.setTitle(request.getTitle().trim());
        return mapProfile(profileRepository.save(profile));
    }

    @Override
    @Transactional
    public void deleteProfile(String email, Long profileId) {
        User user = requireUser(email);
        BrandKitProfile profile = requireProfile(user.getId(), profileId);
        referenceImageRepository.deleteByProfileId(profile.getId());
        profileRepository.delete(profile);
    }

    @Override
    @Transactional
    public BrandKitCheckDetailResponse runCheck(
            String email,
            Long profileId,
            String profileTitle,
            List<MultipartFile> refImages,
            List<MultipartFile> checkImages) {
        User user = requireUser(email);
        List<MultipartFile> refs = normalizeFiles(refImages);
        List<MultipartFile> assets = normalizeFiles(checkImages);
        validateCheckInput(refs, assets);

        return workflowUsageService.track(user, WorkflowType.BRAND_CHECK, null, () -> {
            BrandKitProfile profile = resolveProfileForCheck(user, profileId, profileTitle);

            List<UploadedImage> uploadedRefs = uploadImages(user.getId(), refs, "ref");
            List<UploadedImage> uploadedAssets = uploadImages(user.getId(), assets, "asset");

            JsonNode reportNode = callBrandCheck(refs, assets);
            String reportJson = writeJson(reportNode);

            updateProfileFromReport(profile, reportNode, uploadedRefs);

            BrandKitCheck check = checkRepository.save(BrandKitCheck.builder()
                    .user(user)
                    .profile(profile)
                    .status(BrandKitCheckStatus.COMPLETED)
                    .avgBrandScore(extractAvgScore(reportNode))
                    .totalAssets(extractTotalAssets(reportNode, assets.size()))
                    .reportJson(reportJson)
                    .build());

            saveCheckAssets(check, uploadedAssets, reportNode);
            profile.setLastCheckedAt(LocalDateTime.now());
            profileRepository.save(profile);

            return mapCheckDetail(check, reportNode, checkAssetRepository.findByCheckIdOrderByCreatedAtAsc(check.getId()));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BrandKitCheckSummaryResponse> listChecks(String email, int page, int size) {
        User user = requireUser(email);
        int safeSize = Math.min(Math.max(size, 1), 50);
        return checkRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(Math.max(page, 0), safeSize))
                .map(this::mapCheckSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public BrandKitCheckDetailResponse getCheck(String email, Long checkId) {
        User user = requireUser(email);
        BrandKitCheck check = requireCheck(user.getId(), checkId);
        JsonNode reportNode = readJson(check.getReportJson());
        List<BrandKitCheckAsset> assets = checkAssetRepository.findByCheckIdOrderByCreatedAtAsc(check.getId());
        return mapCheckDetail(check, reportNode, assets);
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private BrandKitProfile requireProfile(Long userId, Long profileId) {
        return profileRepository.findByIdAndUserId(profileId, userId)
                .orElseThrow(() -> new RuntimeException("Brand kit profile not found"));
    }

    private BrandKitCheck requireCheck(Long userId, Long checkId) {
        return checkRepository.findByIdAndUserId(checkId, userId)
                .orElseThrow(() -> new RuntimeException("Brand check not found"));
    }

    private Workspace resolveWorkspace(User user, Long workspaceId) {
        if (workspaceId == null) {
            return null;
        }
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new RuntimeException("Workspace not found"));
        if (!workspace.getOwner().getId().equals(user.getId())) {
            throw new RuntimeException("Bạn không có quyền gắn brand kit vào workspace này");
        }
        return workspace;
    }

    private BrandKitProfile resolveProfileForCheck(User user, Long profileId, String profileTitle) {
        if (profileId != null) {
            return requireProfile(user.getId(), profileId);
        }
        String title = profileTitle != null && !profileTitle.isBlank()
                ? profileTitle.trim()
                : "Brand Kit";
        return profileRepository.save(BrandKitProfile.builder()
                .user(user)
                .title(title)
                .build());
    }

    private List<MultipartFile> normalizeFiles(List<MultipartFile> files) {
        if (files == null) {
            return List.of();
        }
        return files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .collect(Collectors.toList());
    }

    private void validateCheckInput(List<MultipartFile> refs, List<MultipartFile> assets) {
        if (refs.isEmpty()) {
            throw new IllegalArgumentException("Cần ít nhất 1 ảnh REF");
        }
        if (assets.isEmpty()) {
            throw new IllegalArgumentException("Cần ít nhất 1 ảnh ấn phẩm cần kiểm tra");
        }
        if (refs.size() > MAX_REF_IMAGES) {
            throw new IllegalArgumentException("Tối đa " + MAX_REF_IMAGES + " ảnh REF");
        }
        if (assets.size() > MAX_CHECK_IMAGES) {
            throw new IllegalArgumentException("Tối đa " + MAX_CHECK_IMAGES + " ảnh ấn phẩm");
        }
        long totalBytes = 0;
        for (MultipartFile file : refs) {
            uploadSizeValidator.validateImage(file);
            totalBytes += file.getSize();
        }
        for (MultipartFile file : assets) {
            uploadSizeValidator.validateImage(file);
            totalBytes += file.getSize();
        }
        uploadSizeValidator.validateRequestTotal(totalBytes);
    }

    private JsonNode callBrandCheck(List<MultipartFile> refs, List<MultipartFile> assets) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        for (MultipartFile ref : refs) {
            body.add("ref_images", AiServerClient.toFileResource(ref));
        }
        for (MultipartFile asset : assets) {
            body.add("check_images", AiServerClient.toFileResource(asset));
        }
        return aiServerClient.brandCheck(body);
    }

    private void updateProfileFromReport(BrandKitProfile profile, JsonNode reportNode, List<UploadedImage> uploadedRefs) {
        JsonNode dnaNode = reportNode.get("visual_dna");
        if (dnaNode != null && !dnaNode.isNull()) {
            profile.setVisualDnaJson(writeJson(dnaNode));
        }
        referenceImageRepository.deleteByProfileId(profile.getId());
        int order = 0;
        for (UploadedImage uploaded : uploadedRefs) {
            referenceImageRepository.save(BrandKitReferenceImage.builder()
                    .profile(profile)
                    .imageUrl(uploaded.url())
                    .fileName(uploaded.fileName())
                    .fileSizeBytes(uploaded.sizeBytes())
                    .sortOrder(order++)
                    .build());
        }
        profileRepository.save(profile);
    }

    private void saveCheckAssets(BrandKitCheck check, List<UploadedImage> uploadedAssets, JsonNode reportNode) {
        Map<String, JsonNode> assetDetails = new HashMap<>();
        JsonNode details = reportNode.get("asset_details");
        if (details != null && details.isArray()) {
            for (JsonNode node : details) {
                String assetId = node.path("asset_id").asText(null);
                if (assetId != null) {
                    assetDetails.put(assetId, node);
                }
            }
        }

        for (UploadedImage uploaded : uploadedAssets) {
            JsonNode detail = assetDetails.get(uploaded.fileName());
            BigDecimal score = null;
            String severity = null;
            if (detail != null) {
                if (detail.has("brand_score")) {
                    score = BigDecimal.valueOf(detail.get("brand_score").asDouble())
                            .setScale(1, RoundingMode.HALF_UP);
                }
                severity = detail.path("severity").asText(null);
            }
            checkAssetRepository.save(BrandKitCheckAsset.builder()
                    .check(check)
                    .imageUrl(uploaded.url())
                    .fileName(uploaded.fileName())
                    .brandScore(score)
                    .severity(severity)
                    .build());
        }
    }

    private List<UploadedImage> uploadImages(Long userId, List<MultipartFile> files, String prefix) {
        List<UploadedImage> uploaded = new ArrayList<>();
        for (MultipartFile file : files) {
            String url = fileService.uploadFile(file);
            uploaded.add(new UploadedImage(
                    url,
                    file.getOriginalFilename() != null ? file.getOriginalFilename() : prefix + ".jpg",
                    file.getSize()));
        }
        return uploaded;
    }

    private BrandKitProfileResponse mapProfile(BrandKitProfile profile) {
        List<BrandKitReferenceImageResponse> refs = referenceImageRepository
                .findByProfileIdOrderBySortOrderAscCreatedAtAsc(profile.getId()).stream()
                .map(img -> BrandKitReferenceImageResponse.builder()
                        .id(img.getId())
                        .imageUrl(img.getImageUrl())
                        .fileName(img.getFileName())
                        .fileSizeBytes(img.getFileSizeBytes())
                        .sortOrder(img.getSortOrder())
                        .createdAt(img.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return BrandKitProfileResponse.builder()
                .id(profile.getId())
                .title(profile.getTitle())
                .workspaceId(profile.getWorkspace() != null ? profile.getWorkspace().getId() : null)
                .visualDna(readVisualDna(profile.getVisualDnaJson()))
                .referenceImages(refs)
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .lastCheckedAt(profile.getLastCheckedAt())
                .build();
    }

    private BrandKitCheckSummaryResponse mapCheckSummary(BrandKitCheck check) {
        return BrandKitCheckSummaryResponse.builder()
                .id(check.getId())
                .profileId(check.getProfile() != null ? check.getProfile().getId() : null)
                .profileTitle(check.getProfile() != null ? check.getProfile().getTitle() : null)
                .status(check.getStatus().name())
                .avgBrandScore(check.getAvgBrandScore())
                .totalAssets(check.getTotalAssets())
                .createdAt(check.getCreatedAt())
                .build();
    }

    private BrandKitCheckDetailResponse mapCheckDetail(
            BrandKitCheck check,
            JsonNode reportNode,
            List<BrandKitCheckAsset> assets) {
        Object report = reportNode != null ? objectMapper.convertValue(reportNode, Object.class) : readJson(check.getReportJson());
        List<BrandKitCheckAssetResponse> assetResponses = assets.stream()
                .map(asset -> BrandKitCheckAssetResponse.builder()
                        .id(asset.getId())
                        .imageUrl(asset.getImageUrl())
                        .fileName(asset.getFileName())
                        .brandScore(asset.getBrandScore())
                        .severity(asset.getSeverity())
                        .build())
                .collect(Collectors.toList());

        return BrandKitCheckDetailResponse.builder()
                .id(check.getId())
                .profileId(check.getProfile() != null ? check.getProfile().getId() : null)
                .profileTitle(check.getProfile() != null ? check.getProfile().getTitle() : null)
                .status(check.getStatus().name())
                .avgBrandScore(check.getAvgBrandScore())
                .totalAssets(check.getTotalAssets())
                .report(report)
                .assets(assetResponses)
                .createdAt(check.getCreatedAt())
                .build();
    }

    private Map<String, Object> readVisualDna(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("Invalid brand check report", e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize brand kit JSON", e);
        }
    }

    private BigDecimal extractAvgScore(JsonNode reportNode) {
        JsonNode overview = reportNode.get("overview");
        if (overview == null || !overview.has("avg_brand_score")) {
            return null;
        }
        return BigDecimal.valueOf(overview.get("avg_brand_score").asDouble())
                .setScale(1, RoundingMode.HALF_UP);
    }

    private int extractTotalAssets(JsonNode reportNode, int fallback) {
        JsonNode overview = reportNode.get("overview");
        if (overview != null && overview.has("total_assets")) {
            return overview.get("total_assets").asInt(fallback);
        }
        return fallback;
    }

    private record UploadedImage(String url, String fileName, long sizeBytes) {}
}
