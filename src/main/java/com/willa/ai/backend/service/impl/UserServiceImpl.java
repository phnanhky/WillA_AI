package com.willa.ai.backend.service.impl;

import com.willa.ai.backend.dto.response.UserResponse;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

@Service
@Slf4j
@Transactional
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JavaMailSender javaMailSender;

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

    @Override
    public void requestStudentVerification(String eduEmail) {
        if (eduEmail == null || (!eduEmail.endsWith(".edu.vn") && !eduEmail.endsWith(".edu"))) {
            throw new RuntimeException("Chỉ chấp nhận email có đuôi .edu.vn hoặc .edu");
        }
        User user = userRepository.findByEmail(eduEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Sinh mã OTP 6 số
        String otp = String.format("%06d", new Random().nextInt(999999));
        user.setStudentOtp(otp);
        user.setStudentOtpExpiry(LocalDateTime.now().plusMinutes(15));
        userRepository.save(user);

        // Gửi email
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(eduEmail);
        message.setSubject("Willa AI - Xác thực tài khoản sinh viên");
        message.setText("Mã OTP xác thực sinh viên của bạn là: " + otp + "\nMã có hiệu lực trong 15 phút.");
        javaMailSender.send(message);
    }

    @Override
    public void confirmStudentVerification(String eduEmail, String otp) {
        User user = userRepository.findByEmail(eduEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getStudentOtp() == null || !user.getStudentOtp().equals(otp)) {
            throw new RuntimeException("Mã OTP không hợp lệ hoặc không chính xác");
        }
        if (user.getStudentOtpExpiry() != null && user.getStudentOtpExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Mã OTP đã hết hạn");
        }

        // Cập nhật trạng thái sinh viên
        user.setIsStudent(true);
        user.setStudentVerifiedAt(LocalDateTime.now());
        user.setStudentOtp(null); // Xóa OTP sau khi dùng xong
        user.setStudentOtpExpiry(null);
        userRepository.save(user);
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
                .isStudent(user.getIsStudent())
                .requiresReview(user.getRequiresReview())
                .studentVerifiedAt(user.getStudentVerifiedAt())
                .firebaseUid(user.getFirebaseUid())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
