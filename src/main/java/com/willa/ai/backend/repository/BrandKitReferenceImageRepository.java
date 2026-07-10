package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.BrandKitReferenceImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BrandKitReferenceImageRepository extends JpaRepository<BrandKitReferenceImage, Long> {

    List<BrandKitReferenceImage> findByProfileIdOrderBySortOrderAscCreatedAtAsc(Long profileId);

    void deleteByProfileId(Long profileId);
}
