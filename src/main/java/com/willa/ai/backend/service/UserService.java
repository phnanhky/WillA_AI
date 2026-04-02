package com.willa.ai.backend.service;

import com.willa.ai.backend.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {
    Page<UserResponse> getAllUsers(Pageable pageable);
    UserResponse getUserById(Long userId);
    UserResponse getMyInfo(String email);
    UserResponse updateUser(Long userId, String fullName, String phoneNumber);
    void activeUser(Long userId);
    void deactivateUser(Long userId);
    void requestStudentVerification(String eduEmail);
    void confirmStudentVerification(String eduEmail, String otp);
}
