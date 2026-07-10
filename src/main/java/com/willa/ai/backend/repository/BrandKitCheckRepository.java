package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.BrandKitCheck;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BrandKitCheckRepository extends JpaRepository<BrandKitCheck, Long> {

    Page<BrandKitCheck> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<BrandKitCheck> findByIdAndUserId(Long id, Long userId);
}
