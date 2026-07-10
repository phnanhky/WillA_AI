package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.BrandKitCheckAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BrandKitCheckAssetRepository extends JpaRepository<BrandKitCheckAsset, Long> {

    List<BrandKitCheckAsset> findByCheckIdOrderByCreatedAtAsc(Long checkId);
}
