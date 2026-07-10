package com.willa.ai.backend.service;

import com.willa.ai.backend.dto.request.CreateBrandKitProfileRequest;
import com.willa.ai.backend.dto.request.UpdateBrandKitProfileRequest;
import com.willa.ai.backend.dto.response.BrandKitCheckDetailResponse;
import com.willa.ai.backend.dto.response.BrandKitCheckSummaryResponse;
import com.willa.ai.backend.dto.response.BrandKitProfileResponse;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface BrandKitService {

    List<BrandKitProfileResponse> listProfiles(String email);

    BrandKitProfileResponse getProfile(String email, Long profileId);

    BrandKitProfileResponse createProfile(String email, CreateBrandKitProfileRequest request);

    BrandKitProfileResponse updateProfile(String email, Long profileId, UpdateBrandKitProfileRequest request);

    void deleteProfile(String email, Long profileId);

    BrandKitCheckDetailResponse runCheck(
            String email,
            Long profileId,
            String profileTitle,
            List<MultipartFile> refImages,
            List<MultipartFile> checkImages);

    Page<BrandKitCheckSummaryResponse> listChecks(String email, int page, int size);

    BrandKitCheckDetailResponse getCheck(String email, Long checkId);
}
