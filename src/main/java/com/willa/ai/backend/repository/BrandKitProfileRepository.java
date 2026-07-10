package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.BrandKitProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BrandKitProfileRepository extends JpaRepository<BrandKitProfile, Long> {

    List<BrandKitProfile> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<BrandKitProfile> findByIdAndUserId(Long id, Long userId);
}
