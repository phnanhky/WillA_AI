package com.willa.ai.backend.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.enums.Role;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByResetToken(String resetToken);
    boolean existsByEmail(String email);
    List<User> findByIsStudentTrueAndStudentVerifiedAtBefore(LocalDateTime time);
    Optional<User> findFirstByRoleOrderByIdAsc(Role role);

    @Modifying
    @Query(value = "UPDATE users SET created_at = :createdAt, updated_at = :createdAt WHERE id = :id", nativeQuery = true)
    void overwriteCreatedAt(@Param("id") Long id, @Param("createdAt") LocalDateTime createdAt);
}
