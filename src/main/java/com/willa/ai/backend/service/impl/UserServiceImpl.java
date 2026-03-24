package com.willa.ai.backend.service.impl;

import com.willa.ai.backend.dto.response.UserResponse;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Slf4j
@Transactional
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        try {
            log.info("Fetching all users with pagination: page={}, size={}", 
                    pageable.getPageNumber(), pageable.getPageSize());
            Page<User> users = userRepository.findAll(pageable);
            return users.map(this::convertToResponse);
        } catch (Exception e) {
            log.error("Error fetching all users: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch users: " + e.getMessage());
        }
    }

    @Override
    public UserResponse getUserById(Long userId) {
        try {
            log.info("Fetching user by ID: {}", userId);
            Optional<User> user = userRepository.findById(userId);
            if (user.isPresent()) {
                return convertToResponse(user.get());
            } else {
                throw new RuntimeException("User not found with ID: " + userId);
            }
        } catch (Exception e) {
            log.error("Error fetching user by ID: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch user: " + e.getMessage());
        }
    }

    @Override
    public UserResponse getMyInfo(String email) {
        try {
            log.info("Fetching user info for email: {}", email);
            Optional<User> user = userRepository.findByEmail(email);
            if (user.isPresent()) {
                return convertToResponse(user.get());
            } else {
                throw new RuntimeException("User not found with email: " + email);
            }
        } catch (Exception e) {
            log.error("Error fetching user info: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch user info: " + e.getMessage());
        }
    }

    @Override
    public UserResponse updateUser(Long userId, String fullName, String phoneNumber) {
        try {
            log.info("Updating user: {}", userId);
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                if (fullName != null && !fullName.isEmpty()) {
                    user.setFullName(fullName);
                }
                if (phoneNumber != null && !phoneNumber.isEmpty()) {
                    user.setPhoneNumber(phoneNumber);
                }
                User updatedUser = userRepository.save(user);
                return convertToResponse(updatedUser);
            } else {
                throw new RuntimeException("User not found with ID: " + userId);
            }
        } catch (Exception e) {
            log.error("Error updating user: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update user: " + e.getMessage());
        }
    }

    @Override
    public void activeUser(Long userId) {
        try {
            log.info("Activating user: {}", userId);
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                user.setIsActive(true);
                userRepository.save(user);
                log.info("User {} activated successfully", userId);
            } else {
                throw new RuntimeException("User not found with ID: " + userId);
            }
        } catch (Exception e) {
            log.error("Error activating user: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to activate user: " + e.getMessage());
        }
    }

    @Override
    public void deactivateUser(Long userId) {
        try {
            log.info("Deactivating user: {}", userId);
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                user.setIsActive(false);
                userRepository.save(user);
                log.info("User {} deactivated successfully", userId);
            } else {
                throw new RuntimeException("User not found with ID: " + userId);
            }
        } catch (Exception e) {
            log.error("Error deactivating user: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to deactivate user: " + e.getMessage());
        }
    }

    private UserResponse convertToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole().name())
                .isEnabled(user.getIsEnabled())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
