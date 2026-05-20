package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.UserLibraryImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserLibraryImageRepository extends JpaRepository<UserLibraryImage, Long> {
    @Query("SELECT i FROM UserLibraryImage i WHERE i.user.id = :userId ORDER BY i.createdAt ASC")
    List<UserLibraryImage> findByUserIdOrderByCreatedAtAsc(@Param("userId") Long userId);

    @Query("SELECT i FROM UserLibraryImage i WHERE i.user.id = :userId AND i.imageUrl = :imageUrl")
    Optional<UserLibraryImage> findByUserIdAndImageUrl(@Param("userId") Long userId, @Param("imageUrl") String imageUrl);
}
