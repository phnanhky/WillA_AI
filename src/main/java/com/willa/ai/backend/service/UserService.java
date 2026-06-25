package com.willa.ai.backend.service;

import com.willa.ai.backend.dto.response.UserResponse;
import com.willa.ai.backend.entity.enums.Gender;
import com.willa.ai.backend.entity.enums.WorkspacePlanTier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {
    Page<UserResponse> getAllUsers(Pageable pageable);
    UserResponse getUserById(Long userId);
    UserResponse getMyInfo(String email);
    UserResponse updateUser(Long userId, String fullName, String phoneNumber, Gender gender, String occupation, java.time.LocalDate dob);
    UserResponse uploadAvatar(String email, MultipartFile file);
    void activeUser(Long userId);
    void deactivateUser(Long userId);
    void requestStudentVerification(String eduEmail);
    void confirmStudentVerification(String eduEmail, String otp);
    UserResponse updateWorkspacePlanTier(Long userId, WorkspacePlanTier tier);
    UserResponse updateUserWorkspacePlan(Long userId, Long workspacePlanId);
}
